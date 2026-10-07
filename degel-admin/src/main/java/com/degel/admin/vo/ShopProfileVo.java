package com.degel.admin.vo;

import com.degel.admin.entity.SysShop;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.Objects;

/**
 * 店铺可自助变更的资料字段（需平台审批）。也是 sys_shop_change 前后快照的 JSON 结构。
 */
@Data
public class ShopProfileVo {

    @NotBlank(message = "店铺名称不能为空")
    @Size(max = 100, message = "店铺名称过长")
    private String shopName;
    private String logo;
    private String announcement;
    private String description;
    private String contactName;
    private String contactPhone;

    public static ShopProfileVo of(SysShop shop) {
        ShopProfileVo vo = new ShopProfileVo();
        vo.setShopName(shop.getShopName());
        vo.setLogo(shop.getLogo());
        vo.setAnnouncement(shop.getAnnouncement());
        vo.setDescription(shop.getDescription());
        vo.setContactName(shop.getContactName());
        vo.setContactPhone(shop.getContactPhone());
        return vo;
    }

    /** null 统一按空串回写，保证"清空公告"这类变更能生效（updateById 会跳过 null 字段） */
    public SysShop toShop(Long shopId) {
        SysShop shop = new SysShop();
        shop.setId(shopId);
        shop.setShopName(shopName);
        shop.setLogo(nvl(logo));
        shop.setAnnouncement(nvl(announcement));
        shop.setDescription(nvl(description));
        shop.setContactName(nvl(contactName));
        shop.setContactPhone(nvl(contactPhone));
        return shop;
    }

    public boolean sameAs(ShopProfileVo o) {
        return Objects.equals(shopName, o.shopName)
                && Objects.equals(nvl(logo), nvl(o.logo))
                && Objects.equals(nvl(announcement), nvl(o.announcement))
                && Objects.equals(nvl(description), nvl(o.description))
                && Objects.equals(nvl(contactName), nvl(o.contactName))
                && Objects.equals(nvl(contactPhone), nvl(o.contactPhone));
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
