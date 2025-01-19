package com.ctey.cpweb.Controller;

import com.ctey.cpmodule.Service.ReqWorkService;
import com.ctey.cpstatic.Entity.TaskStartReq;
import com.feiniaojin.gracefulresponse.GracefulResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController("/api/v1")
public class CPTaskController {
    private final ReqWorkService reqWorkService;

    @Autowired
    public CPTaskController(ReqWorkService reqWorkService) {
        this.reqWorkService = reqWorkService;
    }

    /*
     * 接口可能返回的异常状态(CPHandleException)包括
     * 501, CP Service has been started/连接池服务已启动
     * 501, CP Service has been stopped/连接池服务已停止
     * 503, CP Handler Service has been locked, please try again later/连接池管理程序正在运行中,请稍后再试
     * 500, CP Service inner error/连接池服务内部错误
     */

    /*
     * StartRequestTask()
     * 传递客户端任务,启动连接池工作状态的模拟
     * 参数示例:
       {
          "count": 0,       -> 任务列表个数(暂未使用)
          "workList": [     -> 任务列表明细
            {
              "id": 0,      -> 任务序号(暂未使用)
              "arrive": 0,  -> 到达时间(从发送时间开始,首次尝试获取连接的时间戳时延)
              "keep": 0     -> 保持时间(从成功获取到连接开始,归还连接给连接池的时间戳时延)
            }
          ]
       }
     * @return
     * @Date: 2025/1/8 21:28
     */
    @PostMapping("/start")
    public void startRequestTask(@RequestBody TaskStartReq req) {
        Optional.ofNullable(reqWorkService.requestWorkTaskStart(req))
                .ifPresent(ex -> GracefulResponse.raiseException(ex.getCode(), ex.getMsg()));
    }

    /*
     * ExamineRequestTask()
     * 输出连接池当前存活连接和客户端线程历史状态
     * @return
     * @Date: 2025/1/8 21:28
     */
    @GetMapping("/examine")
    public void examineRequestTask() {
        Optional.ofNullable(reqWorkService.outPutCPExamine())
                .ifPresent(ex -> GracefulResponse.raiseException(ex.getCode(), ex.getMsg()));
    }

    /*
     * HandleStopCPService()
     * 主动停止连接池服务
     * @return
     * @Date: 2025/1/10 14:22
     */
    @GetMapping("/handle/stop")
    public void handleStopCPService() {
        Optional.ofNullable(reqWorkService.handleStopCP())
                .ifPresent(ex -> GracefulResponse.raiseException(ex.getCode(), ex.getMsg()));
    }

    /*
     * HandleRestartCPService()
     * 主动重启连接池服务
     * @return
     * @Date: 2025/1/10 14:22
     */
    @GetMapping("/handle/restart")
    public void handleRestartCPService() {
        Optional.ofNullable(reqWorkService.handleRestartCP())
                .ifPresent(ex -> GracefulResponse.raiseException(ex.getCode(), ex.getMsg()));
    }

}
