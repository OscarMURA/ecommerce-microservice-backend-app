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
public class CartDto implements Serializable {
	
	private static final long serialVersionUID = 1L;
	private Integer cartId;
	private Integer userId;
	
}

