package cn.itcast.wanxinp2p.consumer.controller;

import cn.itcast.wanxinp2p.api.consumer.ConsumerApi;
import cn.itcast.wanxinp2p.api.consumer.model.ConsumerDTO;
import cn.itcast.wanxinp2p.api.consumer.model.ConsumerRegisterDTO;
import cn.itcast.wanxinp2p.api.consumer.model.ConsumerRequest;
import cn.itcast.wanxinp2p.api.depository.model.GatewayRequest;
import cn.itcast.wanxinp2p.common.domain.RestResponse;
import cn.itcast.wanxinp2p.common.util.EncryptUtil;
import cn.itcast.wanxinp2p.consumer.common.SecurityUtil;
import cn.itcast.wanxinp2p.consumer.service.ConsumerService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@Api(value = "用户服务的Controller", tags = "Consumer", description = "用户服务API")
    public class ConsumerController implements ConsumerApi {

        @Autowired
        private ConsumerService consumerService;
        @ApiOperation("测试hello")
        @GetMapping(path = "/hello")
        public String hello(){
            return "hello";
        }

        @ApiOperation("测试hi")
        @PostMapping(path="/hi")
        @ApiImplicitParam(name="name",value = "姓名",required = true,dataType = "String")
        public String hi(String name){
            return "hi,"+name;
        }

        @ApiOperation("用户注册")
        @ApiImplicitParam(name = "consumerRegisterDTO", value = "用户注册", required =
                true,
                dataType = "AccountRegisterDTO", paramType = "body")
        @PostMapping(value = "/consumers")
        public RestResponse register(@RequestBody ConsumerRegisterDTO consumerRegisterDTO){
            consumerService.register(consumerRegisterDTO);
            return RestResponse.success();
        }
        @ApiOperation("过网关受保护资源，进行认证拦截测试")
        @ApiImplicitParam(name = "jsonToken",value = "访问令牌",required = true,
                dataType =
                        "String")
        @GetMapping(value = "/m/consumers/test")
        public RestResponse<String> testResources(String jsonToken) {
            return RestResponse.success(EncryptUtil.decodeUTF8StringBase64(jsonToken));
        }
        @Override
        @ApiOperation("生成开户请求数据")
        @ApiImplicitParam(name = "consumerRequest", value = "开户信息", required = true,
                dataType = "ConsumerRequest", paramType = "body")
        @PostMapping("/my/consumers")
        public RestResponse<GatewayRequest> createConsumer(@RequestBody ConsumerRequest
                                                                   consumerRequest) {
            consumerRequest.setMobile(SecurityUtil.getUser().getMobile());
            return consumerService.createConsumer(consumerRequest);
        }

    @Override
    @ApiOperation("获取登录用户信息")
    @GetMapping("/l/currConsumer/{mobile}")
    public RestResponse<ConsumerDTO> getCurrConsumer(@PathVariable("mobile") String mobile) {
        ConsumerDTO consumerDTO = consumerService
                .getByMobile(mobile);
        return RestResponse.success(consumerDTO);
    }

    @Override
    @ApiOperation("获取登录用户信息")
    @GetMapping("/my/consumers")
    public RestResponse<ConsumerDTO> getMyConsumer() {
        ConsumerDTO consumerDTO = consumerService
                .getByMobile(SecurityUtil.getUser().getMobile());
        return RestResponse.success(consumerDTO);
    }







}
