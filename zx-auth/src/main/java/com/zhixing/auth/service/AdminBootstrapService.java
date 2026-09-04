package com.zhixing.auth.service;

import com.zhixing.api.client.user.UserClient;
import com.zhixing.api.dto.user.BootstrapAdminDTO;
import com.zhixing.api.dto.user.PasswordChangeDTO;
import com.zhixing.auth.common.util.StrongPasswordGenerator;
import com.zhixing.common.exceptions.CommonException;
import feign.codec.DecodeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 首个管理员安全引导：
 * <p>服务启动时若无管理员账号，则生成强随机密码并调用 zx-user 以 BCrypt 加密入库，
 * 同时将初始凭据写入应用根目录的 {@code .bootstrap-credentials} 文件（应被 git 忽略）。</p>
 */
@Slf4j
@Service
public class AdminBootstrapService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserClient userClient;
    private final Path credentialFile;
    private final String adminPhone;
    private final String adminUsername;

    public AdminBootstrapService(UserClient userClient,
                                 @Value("${zx.admin-bootstrap.credential-file:.bootstrap-credentials}") String credentialFile,
                                 @Value("${zx.admin-bootstrap.admin-phone:13800000000}") String adminPhone,
                                 @Value("${zx.admin-bootstrap.admin-username:admin}") String adminUsername) {
        this.userClient = userClient;
        this.credentialFile = resolve(credentialFile);
        this.adminPhone = adminPhone;
        this.adminUsername = adminUsername;
    }

    /**
     * 启动引导：已存在凭据文件或已存在管理员则跳过；否则生成密码、落库并写入凭据文件。
     */
    public void bootstrapIfNeeded() {
        if (Files.exists(credentialFile)) {
            log.info("已存在初始凭据文件 {}，跳过管理员引导", credentialFile.toAbsolutePath());
            return;
        }
        if (Boolean.TRUE.equals(userClient.adminExists())) {
            log.info("已存在管理员账号，跳过管理员引导");
            return;
        }
        String rawPassword = StrongPasswordGenerator.generateStrong();
        BootstrapAdminDTO dto = new BootstrapAdminDTO();
        dto.setCellPhone(adminPhone);
        dto.setUsername(adminUsername);
        dto.setPassword(rawPassword);
        userClient.createBootstrapAdmin(dto);
        writeCredentialFile(rawPassword);
        log.warn("已生成首个管理员初始凭据并写入 {}，首次登录后请立即通过 POST /accounts/password/first-change 修改密码",
                credentialFile.toAbsolutePath());
    }

    /**
     * 首次改密：交由 zx-user 校验原密码（BCrypt）并落库；成功后删除初始凭据文件。
     * <p>feign 将 Decoder 抛出的业务异常包装为 {@link DecodeException}，此处解包还原
     * RDecoder 的业务异常（如"原密码错误"→400），避免被兜底为 500；同时保证校验失败
     * 绝不删除凭据文件（fail-closed）。</p>
     */
    public void changeBootstrapPassword(String cellPhone, String oldPassword, String newPassword) {
        PasswordChangeDTO dto = new PasswordChangeDTO();
        dto.setCellPhone(cellPhone);
        dto.setOldPassword(oldPassword);
        dto.setNewPassword(newPassword);
        try {
            userClient.changeBootstrapPassword(dto);
        } catch (DecodeException e) {
            if (e.getCause() instanceof CommonException ce) {
                throw ce;
            }
            throw e;
        }
        deleteCredentialFile();
    }

    private void writeCredentialFile(String rawPassword) {
        String content = String.join("\n",
                "== 知行智学 zx-learn 首个管理员初始凭据 ==",
                "生成时间：" + LocalDateTime.now().format(FMT),
                "说明：文件仅首次启动生成，登录后请立即修改密码，修改成功后该文件会被自动删除。",
                "手机号/账号：" + adminPhone,
                "初始密码：" + rawPassword + "\n");
        try {
            Files.createDirectories(credentialFile.toAbsolutePath().getParent() == null ? Paths.get(".")
                    : credentialFile.toAbsolutePath().getParent());
            Files.write(credentialFile, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // 凭据落盘失败不阻断鉴权主流程，但需提示
            throw new IllegalStateException("写入初始凭据文件失败：" + credentialFile.toAbsolutePath(), e);
        }
    }

    private void deleteCredentialFile() {
        try {
            Files.deleteIfExists(credentialFile);
            log.info("首次改密成功，已删除初始凭据文件 {}", credentialFile.toAbsolutePath());
        } catch (IOException e) {
            log.warn("删除初始凭据文件失败，请手动删除 {}", credentialFile.toAbsolutePath(), e);
        }
    }

    private static Path resolve(String file) {
        if (file == null || file.isBlank()) {
            throw new IllegalArgumentException("credential-file 不能为空");
        }
        Path path = Paths.get(file);
        return path.isAbsolute() ? path : Paths.get(System.getProperty("user.dir")).resolve(path);
    }
}