package com.yuewei.plm.module.inventory.service.impl;

import com.yuewei.plm.module.inventory.service.InventoryCenterService;
import com.yuewei.plm.module.inventory.vo.InventoryCenterSnapshotVO;
import com.yuewei.plm.module.inventory.vo.InventoryCenterSnapshotVO.InventoryListRow;
import com.yuewei.plm.module.inventory.vo.InventoryCenterSnapshotVO.InventoryTreeNode;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JdbcInventoryCenterService implements InventoryCenterService {

    private static final String ROOT_NODE_ID = "all";
    private static final String ROOT_LABEL = "\u5168\u90e8\u7269\u6599";

    private final JdbcTemplate jdbcTemplate;

    public JdbcInventoryCenterService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public InventoryCenterSnapshotVO snapshot() {
        boolean materialGroupTableExists = materialGroupTableExists();
        List<GroupRow> groups = materialGroupTableExists ? queryGroups() : List.of();
        List<InventoryListRow> items = queryItems(materialGroupTableExists);
        return new InventoryCenterSnapshotVO(List.of(buildRoot(groups, items.size())), items);
    }

    private InventoryTreeNode buildRoot(List<GroupRow> groups, int itemCount) {
        Map<Long, MutableTreeNode> nodes = new LinkedHashMap<>();
        List<MutableTreeNode> roots = new ArrayList<>();
        for (GroupRow group : groups) {
            MutableTreeNode node = new MutableTreeNode(
                "material-group:" + group.materialGroupId(),
                group.displayName(),
                group.nodeType(),
                group.sourceRecordCount(),
                group.groupCode()
            );
            nodes.put(group.materialGroupId(), node);
            if (group.parentMaterialGroupId() == null) {
                roots.add(node);
            } else {
                MutableTreeNode parent = nodes.get(group.parentMaterialGroupId());
                if (parent == null) {
                    roots.add(node);
                } else {
                    parent.children.add(node);
                }
            }
        }
        return new InventoryTreeNode(
            ROOT_NODE_ID,
            ROOT_LABEL,
            "category",
            itemCount,
            null,
            roots.stream().map(MutableTreeNode::toVo).toList()
        );
    }

    private List<GroupRow> queryGroups() {
        return jdbcTemplate.query(
            """
            select material_group_id,
                   parent_material_group_id,
                   coalesce(nullif(trim(normalized_display_name), ''), display_name) as display_name,
                   'category' as node_type,
                   source_record_count,
                   group_code
             from plm_material_group
             where deleted_flag = 0
               and status = 'active'
               and group_level = 1
             order by group_level, sort_order, display_name, material_group_id
            """,
            this::mapGroupRow
        );
    }

    private List<InventoryListRow> queryItems(boolean materialGroupTableExists) {
        String materialGroupSelect = materialGroupTableExists
            ? "coalesce(parent_group.material_group_id, item_group.material_group_id) as material_group_id"
            : "null::bigint as material_group_id";
        String materialGroupJoin = materialGroupTableExists ? """
              left join plm_material_group item_group
                on item_group.material_group_id = i.material_group_id
               and item_group.deleted_flag = 0
              left join plm_material_group parent_group
                on parent_group.material_group_id = item_group.parent_material_group_id
               and parent_group.deleted_flag = 0
            """ : "";
        return jdbcTemplate.query(
            """
            select i.inventory_id,
                   i.inventory_code,
                   i.inventory_name,
                   i.specification,
                   i.quantity,
                   i.stock_uom,
                   i.inventory_type,
                   i.status,
                   i.supplier_name,
                   i.updated_at,
                   p.product_name,
                   p.model,
                   %s
              from plm_inventory i
              left join plm_product p
                on p.product_id = i.product_id and p.deleted_flag = 0
            %s
             where i.deleted_flag = 0
             order by i.updated_at desc, i.inventory_id desc
            """.formatted(materialGroupSelect, materialGroupJoin),
            this::mapInventoryRow
        );
    }

    private GroupRow mapGroupRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new GroupRow(
            resultSet.getLong("material_group_id"),
            nullableLong(resultSet, "parent_material_group_id"),
            defaultText(resultSet.getString("display_name"), "--"),
            defaultText(resultSet.getString("node_type"), "category"),
            resultSet.getInt("source_record_count"),
            resultSet.getString("group_code")
        );
    }

    private InventoryListRow mapInventoryRow(ResultSet resultSet, int rowNum) throws SQLException {
        Long materialGroupId = nullableLong(resultSet, "material_group_id");
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        return new InventoryListRow(
            String.valueOf(resultSet.getLong("inventory_id")),
            materialGroupId == null ? ROOT_NODE_ID : "material-group:" + materialGroupId,
            defaultText(resultSet.getString("inventory_code"), "--"),
            defaultText(resultSet.getString("inventory_name"), "--"),
            defaultText(resultSet.getString("specification"), "--"),
            stock(resultSet.getBigDecimal("quantity"), resultSet.getString("stock_uom")),
            inventoryTypeLabel(resultSet.getString("inventory_type")),
            resultSet.getString("product_name"),
            resultSet.getString("model"),
            defaultText(resultSet.getString("status"), "available"),
            defaultText(resultSet.getString("supplier_name"), "--"),
            updatedAt == null ? "" : updatedAt.toLocalDateTime().toString()
        );
    }

    private boolean materialGroupTableExists() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'plm_material_group'",
                Integer.class
            );
            return count != null && count > 0;
        } catch (DataAccessException exception) {
            return false;
        }
    }

    private static String stock(BigDecimal quantity, String unit) {
        String value = quantity == null ? "0" : quantity.stripTrailingZeros().toPlainString();
        return StringUtils.hasText(unit) ? value + " " + unit.trim() : value;
    }

    private static String inventoryTypeLabel(String inventoryType) {
        return switch (defaultText(inventoryType, "")) {
            case "semi_finished" -> "\u534a\u6210\u54c1";
            case "finished" -> "\u6210\u54c1";
            case "packaging" -> "\u5305\u6750";
            case "tooling" -> "\u6a21\u5177";
            case "fixture" -> "\u6cbb\u5177";
            default -> "\u539f\u6750\u6599";
        };
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private record GroupRow(
        Long materialGroupId,
        Long parentMaterialGroupId,
        String displayName,
        String nodeType,
        Integer sourceRecordCount,
        String groupCode
    ) {
    }

    private static final class MutableTreeNode {
        private final String nodeId;
        private final String label;
        private final String nodeType;
        private final Integer count;
        private final String groupCode;
        private final List<MutableTreeNode> children = new ArrayList<>();

        private MutableTreeNode(String nodeId, String label, String nodeType, Integer count, String groupCode) {
            this.nodeId = nodeId;
            this.label = label;
            this.nodeType = nodeType;
            this.count = count;
            this.groupCode = groupCode;
        }

        private InventoryTreeNode toVo() {
            return new InventoryTreeNode(nodeId, label, nodeType, count, groupCode, children.stream().map(MutableTreeNode::toVo).toList());
        }
    }
}
