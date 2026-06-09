package com.yuewei.plm.service;

import com.yuewei.plm.controller.dto.ProductCreateDTO;
import com.yuewei.plm.controller.dto.ProductQueryDTO;
import com.yuewei.plm.controller.dto.ProductUpdateDTO;
import com.yuewei.plm.common.vo.PageVO;
import com.yuewei.plm.service.vo.ProductCreateResultVO;
import com.yuewei.plm.service.vo.ProductVO;

public interface ProductService {

    PageVO<ProductVO> page(ProductQueryDTO queryDTO);

    ProductVO getById(Long productId);

    ProductCreateResultVO create(ProductCreateDTO createDTO);

    ProductVO update(Long productId, ProductUpdateDTO updateDTO);

    void freeze(Long productId, String operator, String reason);

    void publish(Long productId, String operator);
}
