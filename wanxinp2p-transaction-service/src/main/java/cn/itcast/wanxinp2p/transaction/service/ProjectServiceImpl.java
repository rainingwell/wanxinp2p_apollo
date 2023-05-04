package cn.itcast.wanxinp2p.transaction.service;

import cn.itcast.wanxinp2p.api.consumer.model.BalanceDetailsDTO;
import cn.itcast.wanxinp2p.api.consumer.model.ConsumerDTO;
import cn.itcast.wanxinp2p.api.depository.model.LoanDetailRequest;
import cn.itcast.wanxinp2p.api.depository.model.LoanRequest;
import cn.itcast.wanxinp2p.api.depository.model.ModifyProjectStatusDTO;
import cn.itcast.wanxinp2p.api.depository.model.UserAutoPreTransactionRequest;
import cn.itcast.wanxinp2p.api.repayment.model.ProjectWithTendersDTO;
import cn.itcast.wanxinp2p.api.transaction.model.*;
import cn.itcast.wanxinp2p.common.domain.*;
import cn.itcast.wanxinp2p.common.util.CodeNoUtil;
import cn.itcast.wanxinp2p.common.util.CommonUtil;
import cn.itcast.wanxinp2p.transaction.agent.ConsumerApiAgent;
import cn.itcast.wanxinp2p.transaction.agent.ContentSearchApiAgent;
import cn.itcast.wanxinp2p.transaction.agent.DepositoryAgentApiAgent;
import cn.itcast.wanxinp2p.transaction.common.constant.TradingCode;
import cn.itcast.wanxinp2p.transaction.common.constant.TransactionErrorCode;
import cn.itcast.wanxinp2p.transaction.common.utils.IncomeCalcUtil;
import cn.itcast.wanxinp2p.transaction.common.utils.SecurityUtil;
import cn.itcast.wanxinp2p.transaction.entity.Project;
import cn.itcast.wanxinp2p.transaction.entity.Tender;
import cn.itcast.wanxinp2p.transaction.mapper.ProjectMapper;
import cn.itcast.wanxinp2p.transaction.mapper.TenderMapper;
import cn.itcast.wanxinp2p.transaction.model.LoginUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.micrometer.core.instrument.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {
    @Autowired
    private ConsumerApiAgent consumerApiAgent;

    @Autowired
    private DepositoryAgentApiAgent depositoryAgentApiAgent;

    @Autowired
    private  ConfigService configService;
    @Resource
    private TenderMapper tenderMapper;
    @Override
    public ProjectDTO createProject(ProjectDTO projectDTO) {
        final RestResponse<ConsumerDTO> consumer =
                consumerApiAgent.getCurrConsumer(SecurityUtil.getUser().getMobile());
// 设置用户编码
        projectDTO.setUserNo(consumer.getResult().getUserNo());
// 设置用户id
        projectDTO.setConsumerId(consumer.getResult().getId());
// 生成标的编码
        projectDTO.setProjectNo(CodeNoUtil.getNo(CodePrefixCode.CODE_PROJECT_PREFIX));
// 标的状态修改
        projectDTO.setProjectStatus(ProjectCode.COLLECTING.getCode());
// 标的可用状态修改, 未同步
        projectDTO.setStatus(StatusCode.STATUS_OUT.getCode());
// 设置标的创建时间
        projectDTO.setCreateDate(LocalDateTime.now());
// 设置还款方式
        projectDTO.setRepaymentWay(RepaymentWayCode.FIXED_REPAYMENT.getCode());
// 设置标的类型
        projectDTO.setType("NEW");
// 把dto转换为entity
        final Project project = convertProjectDTOToEntity(projectDTO);
// 设置利率(需要在Apollo上进行配置)
// 年化利率(借款人视图)
        project.setBorrowerAnnualRate(configService.getBorrowerAnnualRate());
// 年化利率(投资人视图)
        project.setAnnualRate(configService.getAnnualRate());
// 年化利率(平台佣金，利差)
        project.setCommissionAnnualRate(configService.getCommissionAnnualRate());
// 债权转让
        project.setIsAssignment(0);
// 设置标的名字, 姓名+性别+第N次借款
// 判断男女
        String sex = Integer.parseInt(consumer.getResult().getIdNumber()
                .substring(16, 17)) % 2 == 0 ? "女士" : "先生";
// 构造借款次数查询条件
        QueryWrapper<Project> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(Project::getConsumerId,
                consumer.getResult().getId());
        project.setName(
                consumer.getResult().getFullname() + sex
                        + "第" + (count(queryWrapper) + 1) + "次借款");
// 保存到数据库
        save(project);
// 设置主键
        projectDTO.setId(project.getId());
        projectDTO.setName(project.getName());
        return projectDTO;
    }
    private Project convertProjectDTOToEntity(ProjectDTO projectDTO) {
        if (projectDTO == null) {
            return null;
        }
        Project project = new Project();
        BeanUtils.copyProperties(projectDTO, project);
        return project;
    }

    @Override
    public PageVO<ProjectDTO> queryProjectsByQueryDTO(ProjectQueryDTO projectQueryDTO,
                                                      String order, Integer pageNo, Integer pageSize, String sortBy){
// 条件构造器
        QueryWrapper<Project> queryWrapper = new QueryWrapper();
// 标的类型
        if (StringUtils.isNotBlank(projectQueryDTO.getType())) {queryWrapper.lambda().eq(Project::getType, projectQueryDTO.getType());
        }
// 起止年化利率(投资人) -- 区间
        if (null != projectQueryDTO.getStartAnnualRate()) {
            queryWrapper.lambda().ge(Project::getAnnualRate,
                    projectQueryDTO.getStartAnnualRate());
        }
        if (null != projectQueryDTO.getEndAnnualRate()) {
            queryWrapper.lambda().le(Project::getAnnualRate,
                    projectQueryDTO.getStartAnnualRate());
        }
// 借款期限 -- 区间
        if (null != projectQueryDTO.getStartPeriod()) {
            queryWrapper.lambda().ge(Project::getPeriod,
                    projectQueryDTO.getStartPeriod());
        }
        if (null != projectQueryDTO.getEndPeriod()) {
            queryWrapper.lambda().le(Project::getPeriod,
                    projectQueryDTO.getEndPeriod());
        }
// 标的状态
        if (StringUtils.isNotBlank(projectQueryDTO.getProjectStatus())) {
            queryWrapper.lambda().eq(Project::getProjectStatus,
                    projectQueryDTO.getProjectStatus());
        }
// 构造分页对象
        Page<Project> page = new Page<>(pageNo, pageSize);
// 处理排序 order值: desc 或者 asc
        if (StringUtils.isNotBlank(order) && StringUtils.isNotBlank(sortBy)) {
            if (order.equalsIgnoreCase("asc")) {
                queryWrapper.orderByAsc(sortBy);
            } else if (order.equalsIgnoreCase("desc")) {
                queryWrapper.orderByDesc(sortBy);
            }
        } else {
//默认按发标时间倒序排序
            queryWrapper.lambda().orderByDesc(Project::getCreateDate);
        }
// 执行查询
        IPage<Project> projectIPage = page(page, queryWrapper);
// ENTITY转换为DTO, 不向外部暴露ENTITY
        List<ProjectDTO> dtoList =
                convertProjectEntityListToDTOList(projectIPage.getRecords());
// 封装结果集
        return new PageVO<>(dtoList, projectIPage.getTotal(), pageNo, pageSize);
    }
    private List<ProjectDTO> convertProjectEntityListToDTOList(List<Project>
                                                                       projectList) {
        if (projectList == null) {
            return null;
        }
        List<ProjectDTO> dtoList = new ArrayList<>();
        projectList.forEach(project -> {
            ProjectDTO projectDTO = new ProjectDTO();
            BeanUtils.copyProperties(project, projectDTO);
            dtoList.add(projectDTO);
        });
        return dtoList;
    }

    @Override
    public String projectsApprovalStatus(Long id, String approveStatus) {
//1.根据id查询标的信息并转换为DTO对象
        Project project= getById(id);
        ProjectDTO projectDTO=convertProjectEntityToDTO(project);
//2.生成流水号(不存在才生成)
        if(StringUtils.isBlank(project.getRequestNo())){
            projectDTO.setRequestNo(CodeNoUtil.getNo(CodePrefixCode
                    .CODE_REQUEST_PREFIX));
            update(Wrappers.<Project>lambdaUpdate().set(Project::getRequestNo,
                    projectDTO.getRequestNo()).eq(Project::getId,id));
        }
// 3.调用存管代理服务同步标的信息
        final RestResponse<String> restResponse = depositoryAgentApiAgent
                .createProject(projectDTO);
        if (DepositoryReturnCode.RETURN_CODE_00000.getCode()
                .equals(restResponse.getResult())) {
// 4.修改状态为: 已发布
            update(Wrappers.<Project>lambdaUpdate().set(Project::getStatus,
                    Integer.parseInt(approveStatus)).eq(Project::getId,
                    id));
            return "success";
        }
// 5.失败抛出一个业务异常
        throw new BusinessException(TransactionErrorCode.E_150113);
    }
    private ProjectDTO convertProjectEntityToDTO(Project project) {
        if (project == null) {
            return null;
        }
        ProjectDTO projectDTO = new ProjectDTO();
        BeanUtils.copyProperties(project, projectDTO);
        return projectDTO;
    }

    @Autowired
    private ContentSearchApiAgent contentSearchApiAgent;
    @Override
    public PageVO<ProjectDTO> queryProjects(ProjectQueryDTO projectQueryDTO,
                                            String order, Integer pageNo, Integer pageSize, String sortBy) {
        RestResponse<PageVO<ProjectDTO>> esResponse =
                contentSearchApiAgent.queryProjectIndex(projectQueryDTO,
                        pageNo, pageSize, sortBy,
                        order);
        if (!esResponse.isSuccessful()) {
            throw new BusinessException(CommonErrorCode.UNKOWN);
        }
        return esResponse.getResult();
    }

    @Override
    public List<ProjectDTO> queryProjectsIds(String ids) {
//1.构造查询条件
        QueryWrapper<Project> queryWrapper = new QueryWrapper<>();
        List<Long> list = new ArrayList<>();
        Arrays.asList(ids.split(",")).forEach(str -> {
            list.add(Long.parseLong(str));
        });
        queryWrapper.lambda().in(Project::getId, list);
//2.执行查询
        List<Project> projects = list(queryWrapper);
        List<ProjectDTO> dtos = new ArrayList<>();
//3.实体转DTO并封装信息
        for (Project project : projects) {
// 实体转换为DTO
            ProjectDTO projectDTO = convertProjectEntityToDTO(project);
// 封装剩余额度
            projectDTO.setRemainingAmount(getProjectRemainingAmount(project));
// 封装标的已投记录数
            projectDTO.setTenderCount(tenderMapper.selectCount(Wrappers
                    .<Tender>lambdaQuery().eq(Tender::getProjectId,
                            project.getId())));
            dtos.add(projectDTO);
        }
        return dtos;
    }
    /**
     * 获取标的剩余可投额度
     * @param project
     * @return
     */
    private BigDecimal getProjectRemainingAmount(Project project) {
// 根据标的id在投标表查询已投金额
        List<BigDecimal> decimalList = tenderMapper.selectAmountInvestedByProjectId(project.getId());
// 求和结果集
        BigDecimal amountInvested = new BigDecimal("0.0");
        for (BigDecimal d : decimalList) {
            amountInvested = amountInvested.add(d);
        }
// 得到剩余额度
        return project.getAmount().subtract(amountInvested);
    }

    @Override
    public List<TenderOverviewDTO> queryTendersByProjectId(Long id) {
        List<Tender> tenderList = tenderMapper.selectList(Wrappers
                .<Tender>lambdaQuery().eq(Tender::getProjectId,
                        id));
        List<TenderOverviewDTO> tenderOverviewDTOList = new ArrayList<>();
        tenderList.forEach(tender -> {
            TenderOverviewDTO tenderOverviewDTO = new TenderOverviewDTO();
            BeanUtils.copyProperties(tender, tenderOverviewDTO);
            tenderOverviewDTO.setConsumerUsername(CommonUtil
                    .hiddenMobile(tenderOverviewDTO.getConsumerUsername()));
            tenderOverviewDTOList.add(tenderOverviewDTO);
        });
        return tenderOverviewDTOList;
    }

    @Override
    public TenderDTO createTender(ProjectInvestDTO projectInvestDTO) {
        //1.前置条件判断
//1.1 判断投标金额是否大于最小投标金额
//获得投标金额
        BigDecimal amount = new BigDecimal(projectInvestDTO.getAmount());
//获得最小投标金额
        BigDecimal miniInvestmentAmount = configService.getMiniInvestmentAmount();
        if (amount.compareTo(miniInvestmentAmount) < 0) {
            throw new BusinessException(TransactionErrorCode.E_150109);
        }
//1.2 判断用户账户余额是否足够
//得到当前登录用户
        LoginUser user = SecurityUtil.getUser();
//通过手机号查询用户信息
        RestResponse<ConsumerDTO>
                restResponse = consumerApiAgent.getCurrConsumer(user.getMobile());
//通过用户编号查询账户余额
        RestResponse<BalanceDetailsDTO>
                balanceDetailsDTORestResponse = consumerApiAgent.getBalance(restResponse.getResult
                ().getUserNo());
        BigDecimal myBalance = balanceDetailsDTORestResponse.getResult().getBalance();
        if (myBalance.compareTo(amount) < 0) {
            throw new BusinessException(TransactionErrorCode.E_150112);
        }
        //1.3 判断标的是否满标，标的状态为FULLY就表示满标
        Project project = getById(projectInvestDTO.getId());
        if (project.getProjectStatus().equalsIgnoreCase("FULLY")) {
            throw new BusinessException(TransactionErrorCode.E_150114);
        }
//1.4 判断投标金额是否超过剩余未投金额
        BigDecimal remainingAmount = getProjectRemainingAmount(project);
        if (amount.compareTo(remainingAmount) < 1) {
//1.5 判断此次投标后的剩余未投金额是否满足最小投标金额
//例如：借款人需要借1万 现在已经投标了8千 还剩2千 本次投标1950元
//公式：此次投标后的剩余未投金额 = 目前剩余未投金额 - 本次投标金额
            BigDecimal subtract = remainingAmount.subtract(amount);
            int result = subtract.compareTo(configService.getMiniInvestmentAmount());
            if (result < 0) {
                throw new BusinessException(TransactionErrorCode.E_150111);
            }
//2. 保存投标信息并发送给存管代理服务
//3. 根据结果更新投标状态
        } else {
            throw new BusinessException(TransactionErrorCode.E_150110);
        }
        //2.1 保存投标信息, 数据状态为: 未发布
// 封装投标信息
        final Tender tender = new Tender();
// 投资人投标金额( 投标冻结金额 )
        tender.setAmount(amount);
// 投标人用户标识
        tender.setConsumerId(restResponse.getResult().getId());
        tender.setConsumerUsername(restResponse.getResult().getUsername());
// 投标人用户编码
        tender.setUserNo(restResponse.getResult().getUserNo());
// 标的标识
        tender.setProjectId(projectInvestDTO.getId());
// 标的编码
        tender.setProjectNo(project.getProjectNo());
// 投标状态
        tender.setTenderStatus(TradingCode.FROZEN.getCode());
// 创建时间
        tender.setCreateDate(LocalDateTime.now());
// 请求流水号
        tender.setRequestNo(CodeNoUtil.getNo(CodePrefixCode.CODE_REQUEST_PREFIX));
// 可用状态
        tender.setStatus(0);
        tender.setProjectName(project.getName());
        // 标的期限(单位:天)
        tender.setProjectPeriod(project.getPeriod());
// 年化利率(投资人视图)
        tender.setProjectAnnualRate(project.getAnnualRate());
// 保存到数据库
        tenderMapper.insert(tender);
//2.2 发送数据给存管代理服务
// 构造请求数据
        UserAutoPreTransactionRequest userAutoPreTransactionRequest = new
                UserAutoPreTransactionRequest();
// 冻结金额
        userAutoPreTransactionRequest.setAmount(amount);
// 预处理业务类型
        userAutoPreTransactionRequest.setBizType(PreprocessBusinessTypeCode.TENDER.getCode());
// 标的号
        userAutoPreTransactionRequest.setProjectNo(project.getProjectNo());
// 请求流水号
        userAutoPreTransactionRequest.setRequestNo(tender.getRequestNo());
// 投资人用户编码
        userAutoPreTransactionRequest.setUserNo(restResponse.getResult().getUserNo());
// 设置 关联业务实体标识
        userAutoPreTransactionRequest.setId(tender.getId());
// 远程调用存管代理服务
        RestResponse<String> response = depositoryAgentApiAgent.userAutoPreTransaction(userAutoPreTransactionRequest);
// 3.1 判断结果
        if (DepositoryReturnCode.RETURN_CODE_00000.getCode()
                .equals(response.getResult())) {
// 3.2 修改状态为: 已发布
            tender.setStatus(1);
            tenderMapper.updateById(tender);
// 3.3 投标成功后判断标的是否已投满, 如果满标, 更新标的状态
            BigDecimal remainAmount = getProjectRemainingAmount(project);
            if (remainAmount.compareTo(new BigDecimal(0)) == 0) {
                project.setProjectStatus(ProjectCode.FULLY.getCode());
                updateById(project);
            }
// 3.4 转换为dto对象并封装数据
            TenderDTO tenderDTO = convertTenderEntityToDTO(tender);
// 封装标的信息
            project.setRepaymentWay(RepaymentWayCode.FIXED_REPAYMENT.getDesc());
            tenderDTO.setProject(convertProjectEntityToDTO(project));
// 封装预期收益
// 根据标的期限计算还款月数
            final Double ceil = Math.ceil(project.getPeriod() / 30.0);
            Integer month = ceil.intValue();
//计算预期收益
            tenderDTO.setExpectedIncome(IncomeCalcUtil
                    .getIncomeTotalInterest(new
                            BigDecimal(projectInvestDTO.getAmount()), configService.getAnnualRate(), month));
            return tenderDTO;
        } else {
// 抛出一个业务异常
            log.warn("投标失败 ! 标的ID为: {}, 存管代理服务返回的状态为: {}",
                    projectInvestDTO.getId(), restResponse.getResult());
            throw new BusinessException(TransactionErrorCode.E_150113);
        }
    }
    private TenderDTO convertTenderEntityToDTO(Tender tender) {
        if (tender == null) {
            return null;
        }
        TenderDTO tenderDTO = new TenderDTO();
        BeanUtils.copyProperties(tender, tenderDTO);
        return tenderDTO;
    }

    @Override
    @Transactional(rollbackFor = BusinessException.class)
    public String loansApprovalStatus(Long id, String approveStatus, String
            commission) {
// 第一阶段：生成放款明细
// 获取标的信息
        final Project project = getById(id);// 构造查询参数, 获取所有投标信息
        QueryWrapper<Tender> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(Tender::getProjectId, id);
        final List<Tender> tenderList = tenderMapper.selectList(queryWrapper);
// 生成还款明细
        final LoanRequest loanRequest = generateLoanRequest(project, tenderList,
                commission);
// 第二阶段：放款
// 请求存管代理服务
        final RestResponse<String> restResponse = depositoryAgentApiAgent
                .confirmLoan(loanRequest);
        if(DepositoryReturnCode.RETURN_CODE_00000.getCode()
                .equals(restResponse.getResult())) {
// 响应成功, 更新投标信息: 已放款
            updateTenderStatusAlreadyLoan(tenderList);
// 第三阶段：修改标的业务状态
// 调用存管代理服务，修改状态为还款中
// 构造请求参数
            ModifyProjectStatusDTO modifyProjectStatusDTO = new
                    ModifyProjectStatusDTO();
// 业务实体id
            modifyProjectStatusDTO.setId(project.getId());
// 业务状态
            modifyProjectStatusDTO.setProjectStatus(ProjectCode.REPAYING.getCode());
// 请求流水号
            modifyProjectStatusDTO.setRequestNo(loanRequest.getRequestNo());
// 执行请求
            final RestResponse<String> modifyProjectProjectStatus =
                    depositoryAgentApiAgent.modifyProjectStatus(modifyProjectStatusDTO);
            if (DepositoryReturnCode.RETURN_CODE_00000.getCode()
                    .equals(modifyProjectProjectStatus.getResult())) {
//如果处理成功，就修改标的状态为还款中
                project.setProjectStatus(ProjectCode.REPAYING.getCode());
                updateById(project);
// 第四阶段：启动还款
// 封装调用还款服务请求对象的数据
                ProjectWithTendersDTO projectWithTendersDTO = new
                        ProjectWithTendersDTO();
// 封装标的信息
                projectWithTendersDTO.setProject(convertProjectEntityToDTO(project));
// 封装投标信息
                projectWithTendersDTO.setTenders(
                        convertTenderEntityListToDTOList(tenderList));
// 封装投资人让利
                projectWithTendersDTO.setCommissionInvestorAnnualRate(configService
                        .getCommissionInvestorAnnualRate());
// 封装借款人让利
                projectWithTendersDTO.setCommissionBorrowerAnnualRate(configService
                        .getCommissionBorrowerAnnualRate());
// 调用还款服务, 启动还款(生成还款计划、应收明细)
// 由于涉及到分布式事务，后面单独讲解
                return "审核成功";
            } else {
// 失败抛出一个业务异常
                log.warn("审核满标放款失败 ! 标的ID为: {}, 存管代理服务返回的状态为: {}",
                        project.getId(), restResponse.getResult());
                throw new BusinessException(TransactionErrorCode.E_150113);
            }
        } else {
// 失败抛出一个业务异常
            log.warn("审核满标放款失败 ! 标的ID为: {}, 存管代理服务返回的状态为: {}",
                    project.getId(), restResponse.getResult());
            throw new BusinessException(TransactionErrorCode.E_150113);
        }
    }
    /**
     * 根据标的及投标信息生成放款明细
     */
    public LoanRequest generateLoanRequest(Project project, List<Tender> tenderList,
                                           String commission) {
        LoanRequest loanRequest = new LoanRequest();
// 设置标的id
        loanRequest.setId(project.getId());
// 设置平台佣金
        if (StringUtils.isNotBlank(commission)) {
            loanRequest.setCommission(new BigDecimal(commission));
        }
// 设置标的编码
        loanRequest.setProjectNo(project.getProjectNo());
// 设置请求流水号( 标的不没有需要生成新的 )
        loanRequest.setRequestNo(CodeNoUtil.getNo(CodePrefixCode.CODE_REQUEST_PREFIX));
// 处理放款明细
        List<LoanDetailRequest> details = new ArrayList<>();
        tenderList.forEach(tender -> {
            final LoanDetailRequest loanDetailRequest = new LoanDetailRequest();
// 设置放款金额
            loanDetailRequest.setAmount(tender.getAmount());
// 设置预处理业务流水号
            loanDetailRequest.setPreRequestNo(tender.getRequestNo());
            details.add(loanDetailRequest);
        });
// 设置放款明细
        loanRequest.setDetails(details);
// 返回封装好的数据
        return loanRequest;
    }
    /**
     * 更新投标信息: 已放款
     * @param tenderList
     */
    private void updateTenderStatusAlreadyLoan(List<Tender> tenderList) {
        tenderList.forEach(tender -> {
// 设置状态为已放款
            tender.setTenderStatus(TradingCode.LOAN.getCode());
// 更新数据库
            tenderMapper.updateById(tender);
        });
    }
    private List<TenderDTO> convertTenderEntityListToDTOList(List<Tender> records) {
        if (records == null) {
            return null;
        }
        List<TenderDTO> dtoList = new ArrayList<>();
        records.forEach(tender -> {
            TenderDTO tenderDTO = new TenderDTO();
            BeanUtils.copyProperties(tender, tenderDTO);
            dtoList.add(tenderDTO);
        });
        return dtoList;
    }

    }
