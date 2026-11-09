package com.qsx.appoint.architecture.fixture;

public final class WritableQueryFixtures {
    public interface InheritedWrites extends com.baomidou.mybatisplus.core.mapper.BaseMapper<com.qsx.appoint.bookingquery.persistence.BookingQueryRecord> { }
    public interface AnnotatedWrite { @org.apache.ibatis.annotations.Update("UPDATE x SET y=1") int change(); }
    public interface DisguisedWrite { @org.apache.ibatis.annotations.Select("DELETE FROM x") int pretendRead(); }
    public interface LockingRead { @org.apache.ibatis.annotations.Select("SELECT * FROM x FOR UPDATE") Object lock(); }
    public interface SharedLockRead { @org.apache.ibatis.annotations.Select("SELECT * FROM x FOR SHARE") Object lock(); }
    public interface LegacySharedLockRead { @org.apache.ibatis.annotations.Select("SELECT * FROM x LOCK IN SHARE MODE") Object lock(); }
}
