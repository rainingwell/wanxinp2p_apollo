package cn.itcast.wanxinp2p.depository.controller;

import cn.itcast.wanxinp2p.api.consumer.model.ConsumerRequest;
import cn.itcast.wanxinp2p.api.depository.DepositoryAgentApi;
import cn.itcast.wanxinp2p.api.depository.model.*;
import cn.itcast.wanxinp2p.api.transaction.model.ProjectDTO;
import cn.itcast.wanxinp2p.common.domain.RestResponse;
import cn.itcast.wanxinp2p.depository.service.DepositoryRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 存管代理服务
 */
@Api(value = "存管代理服务", tags = "depository-agent")
@RestController
public class DepositoryAgentController implements DepositoryAgentApi {
    @Autowired
    DepositoryRecordService depositoryRecordService;
    @Override
    @ApiOperation("生成开户请求数据")
    @ApiImplicitParam(name = "consumerRequest", value = "开户信息", required = true,
            dataType = "ConsumerRequest", paramType = "body")
    @PostMapping("/l/consumers")
    public RestResponse<GatewayRequest> createConsumer(@RequestBody ConsumerRequest consumerRequest) {
        return
                RestResponse.success(depositoryRecordService.createConsumer(consumerRequest));
    }
    @Override
    @ApiOperation(value = "向银行存管系统发送标的信息")
    @ApiImplicitParam(name = "projectDTO", value = "向银行存管系统发送标的信息",
            required = true, dataType = "ProjectDTO", paramType = "body")
    @PostMapping("/l/createProject")
    public RestResponse<String> createProject(@RequestBody ProjectDTO projectDTO) {
        DepositoryResponseDTO<DepositoryBaseResponse> depositoryResponse =
                depositoryRecordService.createProject(projectDTO);
        RestResponse<String> restResponse=new RestResponse<String>();
        restResponse.setResult(depositoryResponse.getRespData().getRespCode());
        restResponse.setMsg(depositoryResponse.getRespData().getRespMsg());
        return restResponse;
    }
    @Override
    @ApiOperation(value = "预授权处理")
    @ApiImplicitParam(name = "userAutoPreTransactionRequest",
            value = "向银行存管系统发送投标信息", required = true,
            dataType = "UserAutoPreTransactionRequest", paramType =
            "body")
    @PostMapping("/l/user-auto-pre-transaction")
    public RestResponse<String> userAutoPreTransaction(@RequestBody UserAutoPreTransactionRequest userAutoPreTransactionRequest) {
        DepositoryResponseDTO<DepositoryBaseResponse> depositoryResponse =
                depositoryRecordService.userAutoPreTransaction(userAutoPreTransactionRequest);
        return getRestResponse(depositoryResponse);
    }
    /**
     * 统一处理响应信息
     * @param depositoryResponse
     * @return
     */
    private RestResponse<String>
    getRestResponse(DepositoryResponseDTO<DepositoryBaseResponse>
                            depositoryResponse) {
        final RestResponse<String> restResponse = new RestResponse<>();
        restResponse.setResult(depositoryResponse.getRespData().getRespCode());
        restResponse.setMsg(depositoryResponse.getRespData().getRespMsg());
        return restResponse;
    }

    @Override
    @ApiOperation(value = "审核标的满标放款")
    @ApiImplicitParam(name = "loanRequest", value = "标的满标放款信息", required =
            true, dataType = "LoanRequest", paramType = "body")
    @PostMapping("l/confirm-loan")
    public RestResponse<String> confirmLoan(@RequestBody LoanRequest loanRequest) {
        DepositoryResponseDTO<DepositoryBaseResponse> depositoryResponse =
                depositoryRecordService.confirmLoan(loanRequest);
        return getRestResponse(depositoryResponse);
    }

    @Override
    @ApiOperation(value = "修改标的状态")
    @ApiImplicitParam(name = "modifyProjectStatusDTO", value = "修改标的状态DTO",
            required = true, dataType = "ModifyProjectStatusDTO", paramType =
            "body")
    @PostMapping("l/modify-project-status")
    public RestResponse<String> modifyProjectStatus(@RequestBody
                                                    ModifyProjectStatusDTO modifyProjectStatusDTO) {
        DepositoryResponseDTO<DepositoryBaseResponse> depositoryResponse =
                depositoryRecordService.modifyProjectStatus(modifyProjectStatusDTO);
        return getRestResponse(depositoryResponse);
    }

}

