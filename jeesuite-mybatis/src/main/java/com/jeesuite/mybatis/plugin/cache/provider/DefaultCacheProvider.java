/**
 * 
 */
package com.jeesuite.mybatis.plugin.cache.provider;

import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jeesuite.cache.CacheExpires;
import com.jeesuite.cache.command.RedisBatchCommand;
import com.jeesuite.cache.command.RedisObject;
import com.jeesuite.cache.command.RedisString;
import com.jeesuite.cache.redis.JedisProviderFactory;
import com.jeesuite.mybatis.plugin.cache.CacheHandler;
import com.jeesuite.mybatis.plugin.cache.CacheProvider;

import redis.clients.jedis.JedisCommands;

/**
 * @description <br>
 * @author <a href="mailto:vakinge@gmail.com">vakin</a>
 * @date 2016年11月2日
 */
public class DefaultCacheProvider implements CacheProvider{

	protected static final Logger logger = LoggerFactory.getLogger(DefaultCacheProvider.class);
	//计算关联key集合权重的基数
	private long baseScoreInRegionKeysSet = System.currentTimeMillis()/1000 - CacheExpires.IN_1WEEK;

	@Override
	public <T> T get(String key) {
		return new RedisObject(key).get();
	}

	@Override
	public String getStr(String key){
		return new RedisString(key).get();
	}

	@Override
	public boolean set(String key, Object value, long expired) {
		if(value == null)return false;
		if(value instanceof String){
			return new RedisString(key).set(value.toString(),expired);
		}else{
			return new RedisObject(key).set(value, expired);
		}
		
	}


	@Override
	public boolean remove(String key) {
		return new RedisObject(key).remove();
	}


	@Override
	public void putGroupKeys(String cacheGroupKey, String key,long expireSeconds) {
		long score = calcScoreInRegionKeysSet(expireSeconds);
		JedisCommands commands = JedisProviderFactory.getJedisCommands(null);
		try {			
			commands.zadd(cacheGroupKey, score, key);
			commands.pexpire(cacheGroupKey, expireSeconds * 1000);
		} finally{
			JedisProviderFactory.getJedisProvider(null).release();
		}
	}

	@Override
	public void clearGroupKeys(String cacheGroupKey) {
		JedisCommands commands = JedisProviderFactory.getJedisCommands(null);
		try {	
			Set<String> keys = commands.zrange(cacheGroupKey, 0, -1);
			//删除实际的缓存
			if(keys != null && keys.size() > 0){
				RedisBatchCommand.removeObjects(keys.toArray(new String[0]));
			}
			commands.del(cacheGroupKey);
		} finally{
			JedisProviderFactory.getJedisProvider(null).release();
		}
	}

	@Override
	public void clearGroupKey(String cacheGroupKey, String subKey) {
		JedisCommands commands = JedisProviderFactory.getJedisCommands(null);
		try {			
			commands.zrem(cacheGroupKey, subKey);
			//
			commands.del(subKey);
		} finally{
			JedisProviderFactory.getJedisProvider(null).release();
		}
	}


	@Override
	public void clearExpiredGroupKeys(String cacheGroup) {
		long maxScore = System.currentTimeMillis()/1000 - this.baseScoreInRegionKeysSet;
		JedisCommands commands = JedisProviderFactory.getJedisCommands(null);
		try {			
			commands.zremrangeByScore(cacheGroup, 0, maxScore);
		} finally{
			JedisProviderFactory.getJedisProvider(null).release();
		}
		logger.debug("clearExpiredGroupKeys runing:cacheName:{} , score range:0~{}",cacheGroup,maxScore);
	}
	
	@Override
	public void close() throws IOException {}
	
	/**
	 * 避免关联key集合越积越多，按插入的先后顺序计算score便于后续定期删除。<br>
	 * Score 即为 实际过期时间的时间戳
	 * @return
	 */
	private long calcScoreInRegionKeysSet(long expireSeconds){
		long currentTime = System.currentTimeMillis()/1000;
		long score = currentTime + expireSeconds - this.baseScoreInRegionKeysSet;
		return score;
	}

	
	@Override
	public void clearGroup(String groupName) {

		String cacheGroupKey = groupName + CacheHandler.GROUPKEY_SUFFIX;
		JedisCommands commands = JedisProviderFactory.getJedisCommands(null);
		try {	
			Set<String> keys = commands.zrange(cacheGroupKey, 0, -1);
			//删除实际的缓存
			if(keys != null && keys.size() > 0){
				RedisBatchCommand.removeObjects(keys.toArray(new String[0]));
			}
			commands.del(cacheGroupKey);
			//删除按ID缓存的
			keys = JedisProviderFactory.getMultiKeyCommands(null).keys(groupName +".id:*");
			if(keys != null && keys.size() > 0){
				RedisBatchCommand.removeObjects(keys.toArray(new String[0]));
			}
			
		} finally{
			JedisProviderFactory.getJedisProvider(null).release();
		}
	
	}

}
