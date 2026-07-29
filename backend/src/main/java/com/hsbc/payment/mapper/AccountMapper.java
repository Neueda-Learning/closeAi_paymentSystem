package com.hsbc.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hsbc.payment.entity.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface AccountMapper extends BaseMapper<Account> {

    /**
     * Atomic deduct: balance = balance - delta, only if result >= 0.
     * Returns affected rows (1=success, 0=insufficient funds).
     * Eliminates SELECT-then-SET race condition.
     */
    @Update("UPDATE accounts SET balance = balance - #{delta}, updated_at = NOW() "
          + "WHERE account_number = #{accountNumber} AND balance - #{delta} >= 0")
    int deductBalance(@Param("accountNumber") String accountNumber, @Param("delta") BigDecimal delta);

    /**
     * Atomic credit: balance = balance + delta.
     * Always succeeds — no race condition on credit side.
     */
    @Update("UPDATE accounts SET balance = balance + #{delta}, updated_at = NOW() "
          + "WHERE account_number = #{accountNumber}")
    int creditBalance(@Param("accountNumber") String accountNumber, @Param("delta") BigDecimal delta);
}
