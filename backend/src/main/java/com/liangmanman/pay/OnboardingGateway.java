package com.liangmanman.pay;

/** 收付通二级商户进件（开户）。实现：Mock / Wechat / Disabled，与支付实现同时切换。 */
public interface OnboardingGateway {

    /** 提交进件，返回微信申请单号 */
    String submit(Application app);

    /** 查询进件状态 */
    Result query(String outRequestNo);

    /**
     * 进件资料（小微商户：个人身份证 + 个人银行卡）。
     * 身份证号、姓名、卡号、手机号为明文，只在本次请求中使用，实现类负责按微信要求加密。
     */
    record Application(
            String outRequestNo,
            byte[] idCardFront,
            byte[] idCardBack,
            String idName,
            String idNumber,
            String idValidFrom,
            /** yyyy-MM-dd 或 “长期” */
            String idValidTo,
            String bankName,
            String bankCityCode,
            String bankAccount,
            String mobile,
            String shortName,
            String storeName) {}

    /**
     * state：AUDITING 审核中 / NEED_SIGN 待签约 / APPROVED 完成 / REJECTED 驳回
     */
    record Result(String state, String subMchId, String signUrl, String rejectReason) {}
}
