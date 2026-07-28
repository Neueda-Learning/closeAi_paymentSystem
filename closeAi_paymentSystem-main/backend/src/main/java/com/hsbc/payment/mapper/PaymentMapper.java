package com.hsbc.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hsbc.payment.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}
