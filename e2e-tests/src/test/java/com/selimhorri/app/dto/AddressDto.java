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
public class AddressDto implements Serializable {
	
	private static final long serialVersionUID = 1L;
	private Integer addressId;
	private String fullAddress;
	private String postalCode;
	private String city;
	private Integer userId;
	
}
