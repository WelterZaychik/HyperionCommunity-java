package xyz.Brownie.bean.dto;

import lombok.Data;

@Data
public class UserFollowsDto {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 好友ID
     */
    private Long followsId;


}
