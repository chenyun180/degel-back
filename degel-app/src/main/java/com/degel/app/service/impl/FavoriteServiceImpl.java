package com.degel.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.degel.app.entity.MallFavorite;
import com.degel.app.feign.ProductFeignClient;
import com.degel.app.mapper.MallFavoriteMapper;
import com.degel.app.service.FavoriteService;
import com.degel.app.vo.FavoriteVO;
import com.degel.app.vo.ProductSpuVO;
import com.degel.common.core.Constants;
import com.degel.common.core.R;
import com.degel.app.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品收藏 ServiceImpl（MySQL 持久化；取消收藏物理删除，规避唯一键与逻辑删除冲突）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final MallFavoriteMapper favoriteMapper;
    private final ProductFeignClient productFeignClient;
    private final RedisTemplate<String, Object> redisTemplate;

    @org.springframework.beans.factory.annotation.Value("${degel.app.file-base-url:http://localhost:9999}")
    private String fileBaseUrl;

    @Override
    public void add(Long userId, Long spuId) {
        // 收藏前校验商品存在且对 C 端可见（上架+过审）；不可见不允许收藏
        R<List<ProductSpuVO>> resp = productFeignClient.batchGetSpu(Collections.singletonList(spuId));
        List<ProductSpuVO> spus = (resp != null && resp.getCode() == 200) ? resp.getData() : null;
        ProductSpuVO spu = (spus == null || spus.isEmpty()) ? null : spus.get(0);
        if (spu == null || !Integer.valueOf(1).equals(spu.getStatus())
                || !Integer.valueOf(Constants.AUDIT_APPROVED).equals(spu.getAuditStatus())) {
            throw new BusinessException(40400, "商品不存在或已下架");
        }

        MallFavorite favorite = new MallFavorite();
        favorite.setUserId(userId);
        favorite.setSpuId(spuId);
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            // 撞 uk_user_spu：要么已收藏（幂等），要么历史逻辑删行——复活它
            favoriteMapper.insertOrRevive(userId, spuId);
        }
    }

    @Override
    public void remove(Long userId, Long spuId) {
        // 必须物理删除：@TableLogic 下 BaseMapper.delete() 是逻辑删除，
        // del_flag=1 的行仍占 uk_user_spu，再收藏会撞唯一键被幂等吞掉（假收藏）
        favoriteMapper.physicalDelete(userId, spuId);
    }

    @Override
    public IPage<FavoriteVO> list(Long userId, Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 50);

        Page<MallFavorite> favPage = favoriteMapper.selectPage(new Page<>(p, size),
                new LambdaQueryWrapper<MallFavorite>()
                        .eq(MallFavorite::getUserId, userId)
                        .orderByDesc(MallFavorite::getCreateTime)
                        .orderByDesc(MallFavorite::getId));
        List<MallFavorite> favorites = favPage.getRecords();
        if (CollectionUtils.isEmpty(favorites)) {
            return new Page<>(p, size, favPage.getTotal());
        }

        // 批量实时回查商品信息（查询失败不报错：列表降级为"已失效"展示，收藏记录不丢）
        List<Long> spuIds = favorites.stream().map(MallFavorite::getSpuId).collect(Collectors.toList());
        Map<Long, ProductSpuVO> spuMap = batchGetSpuMap(spuIds);

        List<FavoriteVO> records = favorites.stream().map(fav -> {
            FavoriteVO vo = new FavoriteVO();
            vo.setSpuId(fav.getSpuId());
            vo.setFavoriteTime(fav.getCreateTime());
            ProductSpuVO spu = spuMap.get(fav.getSpuId());
            if (spu != null) {
                vo.setSpuName(spu.getName());
                vo.setMainImage(fileUrl(spu.getMainImage()));
                vo.setMinPrice(spu.getMinPrice());
                vo.setSaleCount(spu.getSaleCount());
                vo.setInvalid(!Integer.valueOf(1).equals(spu.getStatus())
                        || !Integer.valueOf(Constants.AUDIT_APPROVED).equals(spu.getAuditStatus()));
            } else {
                // 商品已删除：仅剩收藏时间，前端置灰
                vo.setInvalid(true);
            }
            return vo;
        }).collect(Collectors.toList());

        Page<FavoriteVO> result = new Page<>(p, size, favPage.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    public boolean isFavorite(Long userId, Long spuId) {
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<MallFavorite>()
                .eq(MallFavorite::getUserId, userId)
                .eq(MallFavorite::getSpuId, spuId));
        return count != null && count > 0;
    }

    /** 批量回查 SPU → id 索引 Map；失败返回空 Map（调用方按已失效处理） */
    private Map<Long, ProductSpuVO> batchGetSpuMap(List<Long> spuIds) {
        try {
            R<List<ProductSpuVO>> resp = productFeignClient.batchGetSpu(spuIds);
            if (resp != null && resp.getCode() == 200 && resp.getData() != null) {
                return resp.getData().stream()
                        .filter(spu -> spu.getId() != null)
                        .collect(Collectors.toMap(ProductSpuVO::getId, s -> s, (a, b) -> a));
            }
            log.warn("[FavoriteService] SPU 批量回查失败: {}", resp == null ? "null" : resp.getMsg());
        } catch (Exception e) {
            log.warn("[FavoriteService] SPU 批量回查异常", e);
        }
        return Collections.emptyMap();
    }

    /** 图片相对路径拼完整 URL（存储为裸 key，展示时补 file-base-url 前缀，同 CartServiceImpl） */
    private String fileUrl(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return key;
        }
        return fileBaseUrl + "/file/view/" + key;
    }
}
