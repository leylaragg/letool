package com.github.leyland.letool.demo.basic.core.proxy;

import com.github.leyland.letool.demo.basic.core.anno.TypeMapper;

import java.util.Date;

/**
 * @ClassName <h2>DateProxy</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class DateProxy {


    @TypeMapper(value = "", description = "投保人证件失效日期", dateFormat = "yyyy-MM-dd")
    private Date applicantIDEndDate;


    public Date getApplicantIDEndDate() {
        return applicantIDEndDate;
    }

    public void setApplicantIDEndDate(Date applicantIDEndDate) {
        this.applicantIDEndDate = applicantIDEndDate;
    }
}
