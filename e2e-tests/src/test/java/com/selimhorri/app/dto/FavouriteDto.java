package com.selimhorri.app.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class FavouriteDto implements Serializable {
	
	private static final long serialVersionUID = 1L;
	private Integer userId;
	private Integer productId;
	private LocalDateTime likeDate;
	
}
