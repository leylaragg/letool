package com.github.leyland.letool.demo.spring.lookup;

/**
 * @ClassName <h2>LookupMethodIn</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class LookupMethodIn {

    public static class LookupMethodInA {

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
        public LookupMethodInC createLookupMethodInC() {
            //注意：这里不需要 new LookupMethodInC(). Spring会通过 <lookup-method/> 标签代理该方法，返回一个Spring bean="lookupMethodInC" 的对象。
            return lookupMethodInC;
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

    public static class LookupMethodInB {
    }

    public static class LookupMethodInC {
    }
}
