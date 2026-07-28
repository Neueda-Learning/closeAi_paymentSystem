package com.hsbc.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hsbc.payment.entity.ExchangeRate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExchangeRateMapper extends BaseMapper<ExchangeRate> {
}
