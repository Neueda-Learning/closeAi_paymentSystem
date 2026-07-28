package com.hsbc.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hsbc.payment.entity.StatusHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StatusHistoryMapper extends BaseMapper<StatusHistory> {

    @Select("SELECT * FROM status_history WHERE payment_id = #{paymentId} ORDER BY changed_at ASC")
    List<StatusHistory> findByPaymentId(String paymentId);
}
