package com.example.wordrecommend_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 郵件服務
 *
 * 功能：
 * - 發送密碼重置郵件
 * - 異步發送（不阻塞主線程）
 *
 * @author kimonos-test
 * @version 1.1（改進郵件樣式）
 * @since 2025-11-05
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${frontend.base-url}")
    private String frontendBaseUrl;

    /**
     * 發送密碼重置郵件（v1.1 - 改進版）
     *
     * 改進：
     * - 更清晰的郵件樣式
     * - 顯示過期時間（而非剩餘時長）
     * - 增加視覺對比度
     * - 更好的移動端適配
     *
     * @param to 收件人 Email
     * @param username 使用者名稱
     * @param token 重置密碼 Token
     * @param expiryTime Token 過期時間（新增參數）
     * @throws MessagingException 郵件發送失敗時拋出
     */
    @Async
    public void sendPasswordResetEmail(String to, String username, String token, LocalDateTime expiryTime)
            throws MessagingException {

        log.info("🔵 開始發送密碼重置郵件: to={}, username={}", to, username);

        try {
            // ========== 步驟 1：生成重置連結 ==========

            String resetUrl = frontendBaseUrl + "/reset-password?token=" + token;

            // 格式化過期時間（顯示為：2025年11月5日 下午5:23）
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");
            String formattedExpiryTime = expiryTime.format(formatter);

            log.debug("重置連結: {}", resetUrl);
            log.debug("過期時間: {}", formattedExpiryTime);

            // ========== 步驟 2：創建郵件內容（改進版）==========

            String subject = "重置您的密碼 - WordRecommend";

            String htmlContent = String.format("""
                <!DOCTYPE html>
                <html lang="zh-TW">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>重置密碼</title>
                    <style>
                        /* 重置樣式 */
                        * {
                            margin: 0;
                            padding: 0;
                            box-sizing: border-box;
                        }
                        
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft JhengHei', 
                                         'PingFang TC', Arial, sans-serif;
                            line-height: 1.6;
                            color: #1a1a1a;
                            background-color: #f5f5f5;
                            padding: 20px;
                        }
                        
                        /* 郵件容器 */
                        .email-container {
                            max-width: 600px;
                            margin: 0 auto;
                            background-color: #ffffff;
                            border-radius: 12px;
                            overflow: hidden;
                            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
                        }
                        
                        /* 頭部區域 */
                        .header {
                            background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                            color: white;
                            padding: 40px 30px;
                            text-align: center;
                        }
                        
                        .header h1 {
                            font-size: 28px;
                            font-weight: 600;
                            margin: 0;
                        }
                        
                        .header-icon {
                            font-size: 48px;
                            margin-bottom: 10px;
                        }
                        
                        /* 內容區域 */
                        .content {
                            padding: 40px 30px;
                            background-color: #ffffff;
                        }
                        
                        .greeting {
                            font-size: 18px;
                            color: #1a1a1a;
                            margin-bottom: 20px;
                        }
                        
                        .greeting strong {
                            color: #667eea;
                        }
                        
                        .message {
                            font-size: 16px;
                            color: #4a4a4a;
                            margin-bottom: 30px;
                            line-height: 1.8;
                        }
                        
                        /* 按鈕區域 */
                        .button-container {
                            text-align: center;
                            margin: 30px 0;
                        }
                        
                        .button {
                            display: inline-block;
                            padding: 16px 40px;
                            background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                            color: white !important;
                            text-decoration: none;
                            border-radius: 8px;
                            font-size: 16px;
                            font-weight: 600;
                            transition: transform 0.2s, box-shadow 0.2s;
                            box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
                        }
                        
                        .button:hover {
                            transform: translateY(-2px);
                            box-shadow: 0 6px 16px rgba(102, 126, 234, 0.5);
                        }
                        
                        /* 連結區域 */
                        .link-section {
                            background-color: #f8f9fa;
                            border-left: 4px solid #667eea;
                            padding: 15px;
                            margin: 20px 0;
                            border-radius: 4px;
                        }
                        
                        .link-label {
                            font-size: 14px;
                            color: #666;
                            margin-bottom: 8px;
                        }
                        
                        .link-text {
                            font-size: 14px;
                            color: #667eea;
                            word-break: break-all;
                            font-family: 'Courier New', monospace;
                        }
                        
                        /* 警告區域 */
                        .warning-box {
                            background-color: #fff8e1;
                            border-left: 4px solid #ffc107;
                            padding: 20px;
                            margin: 30px 0;
                            border-radius: 4px;
                        }
                        
                        .warning-title {
                            font-size: 16px;
                            font-weight: 600;
                            color: #f57c00;
                            margin-bottom: 12px;
                            display: flex;
                            align-items: center;
                        }
                        
                        .warning-icon {
                            font-size: 20px;
                            margin-right: 8px;
                        }
                        
                        .warning-list {
                            list-style: none;
                            padding: 0;
                            margin: 0;
                        }
                        
                        .warning-list li {
                            font-size: 14px;
                            color: #5d4037;
                            margin-bottom: 8px;
                            padding-left: 24px;
                            position: relative;
                            line-height: 1.6;
                        }
                        
                        .warning-list li:before {
                            content: "•";
                            position: absolute;
                            left: 8px;
                            color: #f57c00;
                            font-weight: bold;
                        }
                        
                        /* 過期時間高亮 */
                        .expiry-highlight {
                            background-color: #ffebee;
                            color: #c62828;
                            padding: 2px 6px;
                            border-radius: 4px;
                            font-weight: 600;
                        }
                        
                        /* 底部區域 */
                        .footer {
                            background-color: #f8f9fa;
                            padding: 30px;
                            text-align: center;
                            border-top: 1px solid #e0e0e0;
                        }
                        
                        .footer-text {
                            font-size: 14px;
                            color: #666;
                            margin-bottom: 10px;
                        }
                        
                        .footer-copyright {
                            font-size: 12px;
                            color: #999;
                        }
                        
                        .footer-brand {
                            font-weight: 600;
                            color: #667eea;
                        }
                        
                        /* 移動端適配 */
                        @media only screen and (max-width: 600px) {
                            body {
                                padding: 10px;
                            }
                            
                            .header {
                                padding: 30px 20px;
                            }
                            
                            .header h1 {
                                font-size: 24px;
                            }
                            
                            .content {
                                padding: 30px 20px;
                            }
                            
                            .button {
                                padding: 14px 30px;
                                font-size: 15px;
                            }
                            
                            .greeting {
                                font-size: 16px;
                            }
                            
                            .message {
                                font-size: 15px;
                            }
                        }
                    </style>
                </head>
                <body>
                    <div class="email-container">
                        <!-- 頭部 -->
                        <div class="header">
                            <div class="header-icon">🔐</div>
                            <h1>重置您的密碼</h1>
                        </div>
                        
                        <!-- 內容 -->
                        <div class="content">
                            <p class="greeting">您好，<strong>%s</strong>！</p>
                            
                            <p class="message">
                                我們收到了重置您帳戶密碼的請求。為了保護您的帳戶安全，
                                請點擊下方按鈕完成密碼重置。
                            </p>
                            
                            <!-- 按鈕 -->
                            <div class="button-container">
                                <a href="%s" class="button">立即重置密碼</a>
                            </div>
                            
                            <!-- 連結 -->
                            <div class="link-section">
                                <div class="link-label">如果按鈕無法點擊，請複製以下連結到瀏覽器：</div>
                                <div class="link-text">%s</div>
                            </div>
                            
                            <!-- 警告 -->
                            <div class="warning-box">
                                <div class="warning-title">
                                    <span class="warning-icon">⚠️</span>
                                    重要提醒
                                </div>
                                <ul class="warning-list">
                                    <li>此連結將在 <span class="expiry-highlight">%s</span> 過期</li>
                                    <li>連結只能使用 <strong>一次</strong></li>
                                    <li>如果您沒有發起此請求，請忽略此郵件並確認您的帳戶安全</li>
                                    <li>為了您的帳戶安全，請勿將此連結分享給任何人</li>
                                </ul>
                            </div>
                            
                            <p class="message">
                                如果您有任何問題或需要協助，請隨時聯繫我們的客服團隊。
                            </p>
                            
                            <p class="message">
                                祝您學習愉快！<br>
                                <strong>WordRecommend 團隊</strong>
                            </p>
                        </div>
                        
                        <!-- 底部 -->
                        <div class="footer">
                            <p class="footer-text">這是一封自動發送的郵件，請勿直接回覆。</p>
                            <p class="footer-copyright">
                                &copy; 2025 <span class="footer-brand">WordRecommend</span>. 
                                All rights reserved.
                            </p>
                        </div>
                    </div>
                </body>
                </html>
                """, username, resetUrl, resetUrl, formattedExpiryTime);

            // ========== 步驟 3：創建並發送郵件 ==========

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);

            log.info("✅ 密碼重置郵件發送成功: to={}, expiryTime={}", to, formattedExpiryTime);

        } catch (MessagingException e) {
            log.error("❌ 密碼重置郵件發送失敗: to={}, error={}", to, e.getMessage());
            throw e;
        }
    }

    /**
     * 發送測試郵件
     */
    @Async
    public void sendTestEmail(String to) throws MessagingException {
        log.info("🔵 發送測試郵件: to={}", to);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject("測試郵件 - WordRecommend");
        helper.setText("<h1>測試成功！</h1><p>如果您收到此郵件，表示郵件服務配置正確。</p>", true);

        mailSender.send(message);

        log.info("✅ 測試郵件發送成功: to={}", to);
    }
}