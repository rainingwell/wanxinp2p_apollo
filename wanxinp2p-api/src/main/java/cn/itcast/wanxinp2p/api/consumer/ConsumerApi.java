package cn.itcast.wanxinp2p.api.consumer;

import cn.itcast.wanxinp2p.api.consumer.model.ConsumerDTO;
import cn.itcast.wanxinp2p.api.consumer.model.ConsumerRegisterDTO;
import cn.itcast.wanxinp2p.api.consumer.model.ConsumerRequest;
import cn.itcast.wanxinp2p.api.depository.model.GatewayRequest;
import cn.itcast.wanxinp2p.common.domain.RestResponse;

public interface ConsumerApi {
    /**
     * 用户注册
     * @param consumerRegisterDTO
     * @return
     */
    RestResponse register(ConsumerRegisterDTO consumerRegisterDTO);

    /**
     * 生成开户请求数据
     * @param consumerRequest 开户信息
     * @return
     */
    RestResponse<GatewayRequest> createConsumer(ConsumerRequest consumerRequest);

     RestResponse<ConsumerDTO> getCurrConsumer(String mobile);

    /**
     * 获取当前登录用户
     * @return
     */
    RestResponse<ConsumerDTO> getMyConsumer();
}
