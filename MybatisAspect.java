package com.deepexi.permission.aop;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import com.deepexi.permission.extension.AppRuntimeEnv;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author huochai
 * 20181204
 * 拦截器作用：给各实体对象在增加、修改时，自动添加操作属性信息。
 */


@Component
@Intercepts({@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})})
public class MybatisAspect implements Interceptor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private AppRuntimeEnv appRuntimeEnv;

    /* 拦截处理*/
    public Object intercept(Invocation invocation) throws Throwable {

        //标识数据库的操作语言类型
        SqlCommandType sqlCommandType = null;

        Object[] args = invocation.getArgs();

        logger.info("=====================================>>Mybatis拦截处理  当前线程{}", Thread.currentThread().getId());
        //遍历处理参数，update方法有两个参数，参见Executor类中的update()方法。
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            String className = arg.getClass().getName();
            logger.info("参数类型：{}", className);

            //第一个参数 MappedStatement 根据它判断是否给“操作属性”赋值。
            if (arg instanceof MappedStatement) {
                MappedStatement ms = (MappedStatement) arg;
                sqlCommandType = ms.getSqlCommandType();
                if (sqlCommandType == SqlCommandType.INSERT || sqlCommandType == SqlCommandType.UPDATE) {//如果是“增加”或“更新”操作，则继续进行默认操作信息赋值。否则，则退出
                    continue;
                } else {
                    break;
                }
            }

            //第二个参数处理。（只有第二个程序才能跑到这）
            logger.info("操作类型：{}", sqlCommandType);
            if (null == arg) return invocation.proceed();

            //可能为list需要批量处理
            if (Collection.class.isAssignableFrom(arg.getClass()) || Map.class.isAssignableFrom(arg.getClass())) {

                Map map = (Map) arg;
                //list类型
                if (map.containsKey("list")) {
                    logger.info("===========>>>>>>>批量插入");
                    List<Object> objList = (ArrayList) map.get("list");
                    for (Object o : objList) {
                        setPropertie(o, sqlCommandType);
                    }

                } else {//@param方式传参
                    logger.info("===========>>>>>>>注解@param传参");
                    setPropertie(arg, sqlCommandType);
                }

            } else {//实体类传参
                logger.info("===========>>>>>>>实体类传参");
                setPropertie(arg, sqlCommandType);
            }
        }
        return invocation.proceed();
    }

    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }


    /*赋值*/
    public void setPropertie(Object obj, SqlCommandType sqlCommandType) {
        try {
            //todo user 信息未存到线程     操作人可能不会是user  例如member  赋值之前需要判断值不为空

            BeanUtils.setProperty(obj, "updatedBy",appRuntimeEnv.getUser().getNickname());
            BeanUtils.setProperty(obj, "updatedAt", new Date());

            //insert需要进行维护创建人和创建时间
            if (sqlCommandType == SqlCommandType.INSERT) {
                BeanUtils.setProperty(obj, "createdBy",appRuntimeEnv.getUser().getNickname());
                BeanUtils.setProperty(obj, "createdAt", new Date());
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }catch (NullPointerException e){
            logger.error("赋值为空");
            e.printStackTrace();
        }
    }


    @Override
    public void setProperties(Properties properties) {

    }
}
