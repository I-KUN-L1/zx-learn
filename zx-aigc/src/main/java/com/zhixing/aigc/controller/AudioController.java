package com.zhixing.aigc.controller;

import com.zhixing.common.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 语音接口（TTS / STT）。
 * 生产环境可对接阿里云/腾讯云语音合成与识别，这里提供可替换的占位实现。
 */
@RestController
@RequestMapping("/audio")
@RequiredArgsConstructor
public class AudioController {

    @PostMapping("/tts-stream")
    public R<Map<String, String>> tts(@RequestBody Map<String, String> body) {
        // 生产实现：调用 TTS 服务生成流式 mp3 并返回音频流
        return R.ok(Map.of("text", body.getOrDefault("text", ""), "status", "ok"));
    }

    @PostMapping("/stt")
    public R<Map<String, String>> stt() {
        // 生产实现：接收音频流，返回识别文字
        return R.ok(Map.of("text", "", "status", "ok"));
    }
}
