package com.xbrowse.config;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SQLite ID 生成器
 * 使用原子计数器生成唯一 ID
 */
public class SQLiteIdGenerator implements IdentifierGenerator {

    private static final AtomicLong COUNTER = new AtomicLong(System.currentTimeMillis());

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object)
            throws HibernateException {
        return COUNTER.incrementAndGet();
    }
}
