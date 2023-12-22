package com.github.leyland.letool.demo.spring.mvc.controller;

import com.github.leyland.letool.demo.spring.mvc.pojo.MyModel;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.UUID;

/**
 * @ClassName <h2>SessionAttributesController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@RestController
@SessionAttributes({"myModel", "model"})
public class SessionAttributesController {

    /**
     * 通过@ModelAttribute存入的数据
     */
    @ModelAttribute
    public MyModel model() {
        MyModel myModel = new MyModel();
        myModel.setModelId(111L);
        myModel.setModelType(111);
        myModel.setModelName("ModelAttribute1");
        return myModel;
    }

    @GetMapping("/setsessionAttributes")
    public void handle1(Model model, HttpServletRequest request) {
        //手动存入的model数据
        MyModel myModel = new MyModel();
        myModel.setModelId(222L);
        myModel.setModelType(222);
        myModel.setModelName("ModelAttribute2");
        model.addAttribute("model", myModel);

        //手动存入session的数据
        HttpSession session = request.getSession();
        UUID uuid = UUID.randomUUID();
        System.out.println(uuid);
        session.setAttribute("id", uuid.toString());
    }

    /**
     * 清理session
     */
    @GetMapping("/cleansessionAttributes")
    public void handle2(SessionStatus sessionStatus) {
        sessionStatus.setComplete();
    }
}
