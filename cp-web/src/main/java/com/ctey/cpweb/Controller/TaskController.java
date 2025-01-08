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

    @PostMapping("/api/v1/start")
    public void StartRequestTask(@RequestBody TaskStartReq req) {
        requestWorkService.requestWorkTaskStart(req);
    }

    @GetMapping("/api/v1/examine")
    public void ExamineRequestTask() {
        cpHandlerService.outPutCPExamine();
    }

}
