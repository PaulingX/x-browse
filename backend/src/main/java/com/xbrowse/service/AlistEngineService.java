package com.xbrowse.service;

import com.xbrowse.dto.AlistEngineDTO;
import com.xbrowse.entity.AlistEngine;
import com.xbrowse.repository.AlistEngineRepository;
import com.xbrowse.util.AlistClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Alist 引擎管理服务
 */
@Slf4j
@Service
public class AlistEngineService {

    private final AlistEngineRepository engineRepository;

    /**
     * 客户端缓存，key 为引擎 ID
     */
    private final ConcurrentHashMap<Long, AlistClient> clientCache = new ConcurrentHashMap<>();

    public AlistEngineService(AlistEngineRepository engineRepository) {
        this.engineRepository = engineRepository;
    }

    /**
     * 获取所有引擎列表
     */
    public List<AlistEngineDTO> listEngines() {
        return engineRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 根据 ID 获取引擎
     */
    public AlistEngineDTO getEngine(Long id) {
        AlistEngine engine = engineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("引擎不存在"));
        return toDTO(engine);
    }

    /**
     * 添加引擎
     */
    @Transactional
    public AlistEngineDTO addEngine(AlistEngineDTO dto) {
        log.info("添加引擎: remark={}, url={}", dto.getRemark(), dto.getUrl());
        // 验证连接
        AlistClient client = new AlistClient(dto.getUrl(), dto.getToken());
        String userName = client.getCurrentUser();

        AlistEngine engine = new AlistEngine();
        engine.setRemark(dto.getRemark());
        engine.setUrl(dto.getUrl());
        engine.setToken(dto.getToken());
        engine.setUserName(userName);

        engine = engineRepository.save(engine);

        // 缓存客户端
        clientCache.put(engine.getId(), client);
        log.info("引擎添加成功: id={}, remark={}", engine.getId(), engine.getRemark());

        return toDTO(engine);
    }

    /**
     * 更新引擎
     */
    @Transactional
    public AlistEngineDTO updateEngine(Long id, AlistEngineDTO dto) {
        log.info("更新引擎: id={}, remark={}", id, dto.getRemark());
        AlistEngine engine = engineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("引擎不存在"));

        // 如果地址或令牌变更，验证新连接
        boolean urlChanged = !engine.getUrl().equals(dto.getUrl());
        boolean tokenChanged = !engine.getToken().equals(dto.getToken());

        if (urlChanged || tokenChanged) {
            AlistClient client = new AlistClient(dto.getUrl(), dto.getToken());
            String userName = client.getCurrentUser();
            engine.setUserName(userName);
            clientCache.put(id, client);
        }

        engine.setRemark(dto.getRemark());
        engine.setUrl(dto.getUrl());
        engine.setToken(dto.getToken());

        engine = engineRepository.save(engine);
        return toDTO(engine);
    }

    /**
     * 删除引擎
     */
    @Transactional
    public void deleteEngine(Long id) {
        log.info("删除引擎: id={}", id);
        engineRepository.deleteById(id);
        clientCache.remove(id);
        log.info("引擎删除成功: id={}", id);
    }

    /**
     * 获取或创建 Alist 客户端
     */
    public AlistClient getClient(Long engineId) {
        return clientCache.computeIfAbsent(engineId, id -> {
            AlistEngine engine = engineRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("引擎不存在"));
            return new AlistClient(engine.getUrl(), engine.getToken());
        });
    }

    /**
     * 测试引擎连接，返回错误信息（成功时为null）
     */
    public String testConnection(AlistEngineDTO dto) {
        log.info("测试引擎连接: url={}", dto.getUrl());
        try {
            AlistClient client = new AlistClient(dto.getUrl(), dto.getToken());
            client.getCurrentUser();
            log.info("引擎连接测试成功: url={}", dto.getUrl());
            return null;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null || msg.isEmpty()) {
                msg = e.getClass().getSimpleName();
            }
            // 截断过长的错误信息
            if (msg.length() > 200) {
                msg = msg.substring(0, 200);
            }
            log.warn("测试连接失败: {}", msg);
            return msg;
        }
    }

    /**
     * 实体转 DTO
     */
    private AlistEngineDTO toDTO(AlistEngine engine) {
        AlistEngineDTO dto = new AlistEngineDTO();
        dto.setId(engine.getId());
        dto.setRemark(engine.getRemark());
        dto.setUrl(engine.getUrl());
        dto.setUserName(engine.getUserName());
        // 不返回 token
        return dto;
    }
}
