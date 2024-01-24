package com.github.leyland.letool.demo.spring.lookup;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @ClassName <h2>LookupMethodIn</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Component
public class LookupMethodIn {

    @Component("lookupMethodInA")
    public static class LookupMethodInA {

        @Autowired
        private LookupMethodInB lookupMethodInB;
        /**
         * 实际上lookupMethodInC属性根本你没有注入过
         */
        private LookupMethodInC lookupMethodInC;

        public LookupMethodInB getLookupMethodInB() {
            return lookupMethodInB;
        }

        public void setLookupMethodInB(LookupMethodInB lookupMethodInB) {
            this.lookupMethodInB = lookupMethodInB;
        }

        /**
         * 实际上每一个都是找容器要对象
         */
        public LookupMethodInC getLookupMethodInC() {
            //调用createLookupMethodInC方法
            return createLookupMethodInC();
        }

        /**
         * 将会被动态代理替换的方法，找容器要对象
         */
        @Lookup("lookupMethodInC")
        public LookupMethodInC createLookupMethodInC() {
            //注意：这里不需要 new LookupMethodInC(). Spring会通过 <lookup-method/> 标签代理该方法，返回一个Spring bean="lookupMethodInC" 的对象。
            return null; //return lookupMethodInC
        }

        public void setLookupMethodInC(LookupMethodInC lookupMethodInC) {
            this.lookupMethodInC = lookupMethodInC;
        }

        @Override
        public String toString() {
            return "LookupMethodInA{" +
                    "lookupMethodInB=" + lookupMethodInB +
                    ", lookupMethodInC=" + lookupMethodInC +
                    '}';
        }
    }

    @Component("lookupMethodInB")
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public static class LookupMethodInB {
    }

    @Component("lookupMethodInC")
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public static class LookupMethodInC {
    }
}
