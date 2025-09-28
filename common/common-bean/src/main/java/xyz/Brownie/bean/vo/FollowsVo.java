package xyz.Brownie.bean.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FollowsVo {
    private Long id;

    private String account;

    private String name;

    private String synopsis;

    private String avatar;

}
