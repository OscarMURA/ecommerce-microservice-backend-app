package com.selimhorri.app.dto;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class ProductDto implements Serializable {
	
	private static final long serialVersionUID = 1L;
	private Integer productId;
	private String productTitle;
	private String imageUrl;
	private String sku;
	private Double priceUnit;
	private Integer quantity;
	private Integer categoryId;
	
}
