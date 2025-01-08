package com.ctey.cpweb.Controller;

import com.ctey.cpmodule.Service.CPHandlerService;
import com.ctey.cpmodule.Service.RequestWorkService;
import com.ctey.cpstatic.Entity.TaskStartReq;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskController {
    private final RequestWorkService requestWorkService;
    private final CPHandlerService cpHandlerService;

    @Autowired
    public TaskController(RequestWorkService requestWorkService, CPHandlerService cpHandlerService) {
        this.requestWorkService = requestWorkService;
        this.cpHandlerService = cpHandlerService;
    }

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
    @PostMapping("/api/v1/start")
    public void StartRequestTask(@RequestBody TaskStartReq req) {
        requestWorkService.requestWorkTaskStart(req);
    }

    /*
     * ExamineRequestTask()
     * 输出连接池当前存活连接和客户端线程历史状态
     * @return
     * @Date: 2025/1/8 21:28
     */
    @GetMapping("/api/v1/examine")
    public void ExamineRequestTask() {
        cpHandlerService.outPutCPExamine();
    }

}
