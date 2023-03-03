package cn.itcast.wanxinp2p.api.account;

import cn.itcast.wanxinp2p.api.account.model.AccountDTO;
import cn.itcast.wanxinp2p.api.account.model.AccountLoginDTO;
import cn.itcast.wanxinp2p.api.account.model.AccountRegisterDTO;
import cn.itcast.wanxinp2p.common.domain.RestResponse;

public interface AccountAPI {

    /**
     * 获取手机验证码
     * @param mobile 手机号
     * @return
     */
    RestResponse getSMSCode(String mobile);

    /**
     * 校验手机号和验证码
     * @param mobile 手机号
     * @param key 校验标识
     * @param code 验证码
     * @return
     */
    RestResponse<Integer> checkMobile(String mobile, String key, String code);

    /**
     * 用户注册
     * @param accountRegisterDTO
     * @return
     */
    RestResponse<AccountDTO> register(AccountRegisterDTO accountRegisterDTO);

    /**
     * 用户登录
     * @param accountLoginDTO 封装登录请求数据
     * @return
     */
    RestResponse<AccountDTO> login(AccountLoginDTO accountLoginDTO);
}
