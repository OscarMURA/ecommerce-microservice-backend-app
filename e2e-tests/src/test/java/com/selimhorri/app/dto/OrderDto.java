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
public class OrderDto implements Serializable {
	
	private static final long serialVersionUID = 1L;
	private Integer orderId;
	private String orderDesc;
	private Double orderFee;
	private Integer userId;
	
}
