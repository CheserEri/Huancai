package cn.huancai.erp.module.homeservice.controller;

import cn.huancai.erp.common.ApiResult;
import cn.huancai.erp.module.homeservice.dto.MessageSendForm;
import cn.huancai.erp.module.homeservice.entity.WxMessage;
import cn.huancai.erp.module.homeservice.service.MessageService;
import cn.huancai.erp.module.homeservice.vo.MessageReceiverVO;
import cn.huancai.erp.module.homeservice.vo.MessageVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 家校服务-消息推送：发送消息到家长端微信小程序并查看阅读情况。 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public ApiResult<IPage<MessageVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String msgType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(messageService.list(keyword, msgType, start, end, page, size));
    }

    @PostMapping("/send")
    public ApiResult<WxMessage> send(@RequestBody MessageSendForm form) {
        try {
            return ApiResult.ok(messageService.send(form));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        }
    }

    @GetMapping("/{id}/receivers")
    public ApiResult<IPage<MessageReceiverVO>> receivers(
            @PathVariable Long id,
            @RequestParam(required = false) Integer readFlag,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(messageService.receivers(id, readFlag, page, size));
    }
}
