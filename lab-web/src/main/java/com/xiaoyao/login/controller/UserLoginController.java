/*************************(*****************************************************
 * Copyright (C) 2016 ShenZhen xiaoyue Information Technology Co.,Ltd
 * All Rights Reserved.
 * 本软件为许畅个人开发研制。未经本人正式书面同意，其他任何个人、团体不得使用、
 * 复制、修改或发布本软件.
 *****************************************************************************/
package com.xiaoyao.login.controller;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.easemob.server.example.comm.utils.EmchatOperator;
import com.easemob.server.example.comm.wrapper.ResponseWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaoyao.base.controller.BizBaseController;
import com.xiaoyao.base.model.Person;
import com.xiaoyao.base.util.BeanUtils;
import com.xiaoyao.base.util.JSONUtils;
import com.xiaoyao.base.util.MD5Util;
import com.xiaoyao.login.model.ChatRecord;
import com.xiaoyao.login.model.InviteCode;
import com.xiaoyao.login.model.IsPay;
import com.xiaoyao.login.model.User;
import com.xiaoyao.login.model.UserExample;
import com.xiaoyao.login.service.InviteCodeService;
import com.xiaoyao.login.service.PersonManageService;
import com.xiaoyao.login.service.UserLoginService;
import com.xiaoyao.login.util.LoginUtil;
import com.xiaoyao.upload.util.UploadFileUtil;

/**
 * 逍遥派用户登录Controller
 * 
 * @author 许畅
 * @since JDK1.7
 * @version 2016年8月17日 许畅 新建
 */
@Controller
@RequestMapping(value = "/xiaoyao")
public class UserLoginController extends BizBaseController {

	/** 注入 UserLoginService */
	@Autowired
	private UserLoginService userLoginService;

	/** 注入 inviteCodeService */
	@Autowired
	private InviteCodeService inviteCodeService;

	/** 注入 PersonManageService */
	@Autowired
	private PersonManageService personManageService;

	/**
	 * 用户登录
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("login")
	public void login(HttpServletRequest request, HttpServletResponse response) {
		String username = request(request, "username");// 用户名或者手机号
		String password = request(request, "password");// 密码
		Map<String, String> validateResult = new HashMap<String, String>();
		validateResult.put("username", "用户名或者手机号不能为空.");
		validateResult.put("password", "密码不能为空.");
		if (!validateParamBlank(request, response, validateResult))
			return;

		List<User> users = userLoginService.login(username, password);
		if (CollectionUtils.isEmpty(users)) {
			JSONUtils.ERROR(response, "当前用户没有注册,请先注册.");
		} else {
			User user = users.get(0);
			// 校验密码是否正确
			if (!password.equals(user.getPassword())) {
				JSONUtils.ERROR(response, "当前输入的密码不正确,请重新输入.");
				System.out.println("requestpwd:" + password + ";realpwd:"
						+ user.getPassword());
				return;
			}
			// 未付款不能登录
			if (IsPay.UN_PAY.getValue().equals(user.getIspay())) {
				JSONUtils.ERROR(response, "当前用户注册时未付款,不能登录.");
			} else {
				// 将当前用户信息放入session中
				setCurrentUserAndPerson(request, user);
				JSONUtils.SUCCESS(response, user);
				System.out.println("用户:" + user.getName() + " login in...");
			}
		}
	}

	/**
	 * 获取当前个人信息
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("getCurrentPerson")
	public void getCurrentPerson(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, String> validateResult = new HashMap<String, String>();
		validateResult.put("userId", "用户id不能为空.");
		if (!validateParamBlank(request, response, validateResult))
			return;

		JSONUtils.SUCCESS(response, getCurrentPerson(request));
	}

	/**
	 * 获取当前用户信息
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("getCurrentUser")
	public void getCurrentUser(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, String> validateResult = new HashMap<String, String>();
		validateResult.put("userId", "用户id不能为空.");
		if (!validateParamBlank(request, response, validateResult))
			return;

		JSONUtils.SUCCESS(response, getCurrentUser(request));
	}

	/**
	 * 编辑个人信息
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("editUser")
	public void editUser(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, String> validateResult = new HashMap<String, String>();
		validateResult.put("id", "用户id不能为空.");
		validateResult.put("name", "昵称不能为空.");
		validateResult.put("birthday", "出生年月日不能为空.");
		validateResult.put("address", "住址不能为空.");
		validateResult.put("providerid", "我提供的资源不能为空");
		validateResult.put("requiredid", "我需要的资源不能为空.");
		validateResult.put("city", "城市不能为空.");
		validateResult.put("sex", "性别不能为空.");// 0 男性,1女性

		if (!validateParamBlank(request, response, validateResult))
			return;

		String id = request(request, "id");
		User user = BeanUtils.mapConvert2ToBean(User.class, request);
		// 重新设置是否已付款和权限因为VO中有默认值
		User currentUser = userLoginService.queryUserByPrimaryKey(id);
		user.setIspay(currentUser.getIspay());
		user.setPermission(currentUser.getPermission());

		userLoginService.editUser(user);
		JSONUtils.SUCCESS(response, "更新个人信息成功.");
	}

	/**
	 * 上传个人头像
	 * 
	 * @param multipartFile
	 * @param request
	 * @param response
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	@RequestMapping("uploadHead")
	public void uploadHead(
			@RequestParam(value = "file", required = false) MultipartFile multipartFile,
			HttpServletRequest request, HttpServletResponse response)
			throws IllegalStateException, IOException {
		Map<String, String> validateResult = new HashMap<String, String>();
		validateResult.put("userId", "用户id不能为空.");
		if (!validateParamBlank(request, response, validateResult))
			return;

		User user = getCurrentUser(request);
		// 获取upload上传文件真实路径
		String realPath = request.getSession().getServletContext()
				.getRealPath("head");
		String fileName = multipartFile.getOriginalFilename();
		System.out.println("originalFilename文件名:" + fileName);
		// 校验文件必须是jpg等图片格式文件
		String contentType = multipartFile.getContentType();
		if (contentType.indexOf("image") < 0) {
			JSONUtils.ERROR(response, "上传的文件存在非法格式,不允许上传");
			return;
		}
		if (!UploadFileUtil.checkIsImage(multipartFile.getInputStream())) {
			JSONUtils.ERROR(response, "上传的文件非图片格式,不允许上传");
			return;
		}
		fileName = UploadFileUtil.createFileName(fileName, user.getId());
		user.setUrl(fileName);
		userLoginService.updateByByPrimaryKey(user);

		// 文件上传至服务器
		File file = new File(realPath, fileName);
		if (!file.exists())
			file.mkdirs();
		else {
			file.delete();
		}
		multipartFile.transferTo(file);

		// 返回上传图片id
		JSONUtils.SUCCESS(response, "上传文件[" + fileName + "]成功.");
	}

	/**
	 * 获取我的弟子
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("getChildrens")
	public void getChildrens(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, String> validateResult = new HashMap<String, String>();
		validateResult.put("personId", "人物的id不能为空.");
		if (!validateParamBlank(request, response, validateResult))
			return;

		Integer parentId = Integer.parseInt(request(request, "personId"));
		JSONUtils.SUCCESS(response,
				personManageService.queryChildsByParent(parentId));
	}

	/**
	 * 用户注册(先注册 再付款 最后完善个人信息)
	 * 
	 * @param request
	 * @param response
	 * @return
	 */
	@RequestMapping("register")
	public void register(HttpServletRequest request,
			HttpServletResponse response) {
		// 手机号,密码,验证码
		Map<String, String> params = parseParams(request, "register",
				new String[] { "phone", "password", "code" });
		String phone = params.get("phone");
		String password = params.get("password");
		String code = params.get("code");

		// 校验参数是否为空
		Map<String, String> validateResult = new HashMap<String, String>();
		validateResult.put("phone", "手机号不能为空.");
		validateResult.put("password", "密码不能为空.");
		validateResult.put("code", "验证码不能为空.");
		if (!validateParamBlank(request, response, validateResult))
			return;

		// 校验是否已注册并且是已付款
		if (!this.validateRegist(request, response))
			return;

		Object sessionCode = request.getSession().getAttribute("code");
		if (sessionCode == null) {
			JSONUtils.ERROR(response, "验证码已过期,请重新发送验证码.");
			return;
		}
		if (String.valueOf(sessionCode).equals(code)) {
			// 创建环信用户
			ResponseWrapper rsp = EmchatOperator.createIMUser(phone, password,
					"新建用户");
			System.out.println("createIMUser:" + rsp.getResponseBody());
			// 保存本地用户信息
			User user = new User();
			user.setPhone(phone);
			user.setPassword(password);
			userLoginService.saveUser(user);
			request.getSession().setAttribute("user", user);
			// 创建邀请码并创建聊天室
			this.createInviteCode(request);

			JSONUtils.SUCCESS(response, user.getId());
		} else {
			JSONUtils.PARAM_ERROR(response, "验证码不匹配请重新输入验证码.");
		}
	}

	/**
	 * 注册校验是否已注册,已付款
	 * 
	 * @param request
	 * @param response
	 * @return
	 */
	private boolean validateRegist(HttpServletRequest request,
			HttpServletResponse response) {
		String phone = request(request, "phone");
		List<User> users = queryUserByPhone(phone);
		if (!CollectionUtils.isEmpty(users)) {
			if (users.get(0).getIspay() == IsPay.IS_PAY.getValue()) {
				JSONUtils.ERROR(response, "当前用户已注册并且已付款,不能重复注册.");
				return false;
			} else {
				// 已存在的用户为付款注册直接返回userId
				JSONUtils.SUCCESS(response, users.get(0).getId());
				return false;
			}
		}
		return true;
	}

	/**
	 * 付款操作(先注册 再付款 最后完善个人信息)
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("payment")
	public void payment(HttpServletRequest request, HttpServletResponse response) {
		String inviteCode = request(request, "inviteCode");// 邀请码
		String userId = request(request, "userId");
		// 校验参数是否为空
		Map<String, String> validateResult = new HashMap<String, String>();
		validateResult.put("inviteCode", "邀请码不能为空.");
		validateResult.put("userId", "用户id不能为空.");
		if (!validateParamBlank(request, response, validateResult))
			return;
		// 校验邀请码是否有效
		if (!inviteCodeService.isExsit(inviteCode)) {
			JSONUtils.ERROR(response, "当前邀请码不存在,请检查邀请码的正确性.");
			return;
		}

		// 校验付款是否成功
		User user = userLoginService.queryUserByPrimaryKey(Integer
				.parseInt(userId));
		if (IsPay.UN_PAY.getValue() == user.getIspay()) {
			// 更新已付款
			userLoginService.updateIsPay(user);
			// 付款并保存person信息反写金额等业务操作
			Person person = userLoginService.saveUser(user, inviteCode);
			setCurrentPerson(request, person);
			setCurrentUser(request, user);
			JSONUtils.SUCCESS(response, user);
			return;
		}
		// 付款成功则直接返回user信息
		JSONUtils.SUCCESS(response, user);
	}

	/**
	 * 确认提交(注册完善个人信息)
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("confirmSubmit")
	public void confirmSubmit(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, String> validateResult = new HashMap<String, String>();
		validateResult.put("name", "姓名不能为空.");
		validateResult.put("sex", "性别不能为空.");
		validateResult.put("birthday", "出生年月不能为空.");
		validateResult.put("address", "收货地址不能为空.");
		validateResult.put("phone", "手机号码不能为空.");
		validateResult.put("city", "所在城市不能为空.");
		if (!validateParamBlank(request, response, validateResult))
			return;

		String phone = request(request, "phone");// 手机号
		User user = getCurrentUser(request);
		if (!phone.equals(user.getPhone())) {
			JSONUtils.PARAM_ERROR(response, "当前手机号与注册手机号不一致,请检查手机号码是否正确.");
			return;
		}
		Map<String, Object> map = BeanUtils.beanConverToMap(user);
		@SuppressWarnings("unchecked")
		Map<String, Object> param = request.getParameterMap();
		map.putAll(param);
		user = BeanUtils.mapConvert2ToBean(User.class, map);
		setCurrentUser(request, user);
		// 更新User信息
		if (userLoginService.updateUser(user)) {
			// 更新群名称
			this.updateChatGroup(user);
			JSONUtils.SUCCESS(response, "完善个人信息成功.");
		} else {
			JSONUtils.ERROR(response, "个人信息更新失败.");
		}
	}

	/**
	 * 更新聊天群名称
	 * 
	 * @param user
	 */
	private void updateChatGroup(User user) {
		List<InviteCode> inviteCodes = inviteCodeService.queryInviteCode(user
				.getId());
		if (!CollectionUtils.isEmpty(inviteCodes)) {
			InviteCode inviteCode = inviteCodes.get(0);
			// 修改聊天室名称为邀请码
			EmchatOperator.modifyChatGroup(inviteCode.getChatroomId(),
					user.getName() + "的徒弟", "[" + user.getName() + "]新建群" + "_"
							+ inviteCode.getNumber(), user.getPhone(),
					new String[] { user.getPhone() });
		}
	}

	/**
	 * 个人测试专用
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("test")
	public void test(HttpServletRequest request, HttpServletResponse response) {
		ResponseWrapper resp = EmchatOperator.sendMessage("users",
				new String[] { "18627014275" }, "10001", null, "hello");

		JSONUtils.SUCCESS(response, resp);
	}

	/**
	 * 获取当前用户好友
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("getFriends")
	public void getFriends(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, String> validateResult = new HashMap<String, String>();
		validateResult.put("userId", "用户id不能为空.");
		if (!validateParamBlank(request, response, validateResult))
			return;

		String userId = request(request, "userId");
		User user = userLoginService.queryUserByPrimaryKey(userId);
		ResponseWrapper resp = EmchatOperator.getFriends(user.getPhone());
		if (resp.hasError()) {
			JSONUtils.ERROR(response, resp.toString());
			return;
		}

		ObjectNode node = (ObjectNode) resp.getResponseBody();
		JsonNode data = node.get("data");
		JSONArray array = JSON.parseArray(data.toString());
		String[] phones = array.toArray(new String[] {});
		JSONUtils.SUCCESS(response,
				userLoginService.queryUserByPhones(Arrays.asList(phones)));
	}

	/**
	 * 通过用户id集合获取用户组信息
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("queryUserByIds")
	public void queryUserByIds(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, String> validateResult = new HashMap<String, String>();
		validateResult.put("userIds", "用户id不能为空");
		if (!validateParamBlank(request, response, validateResult))
			return;

		String[] userIds = request(request, "userIds").split(",");
		List<Integer> ids = new ArrayList<Integer>();
		for (String userId : userIds) {
			ids.add(Integer.parseInt(userId));
		}
		JSONUtils.SUCCESS(response, userLoginService.queryUserByIds(ids));
	}

	/**
	 * 通过手机号集合获取用户组信息
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("queryUserByPhones")
	public void queryUserByPhones(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, String> validateResult = new HashMap<String, String>();
		validateResult.put("phones", "手机号不能为空");
		if (!validateParamBlank(request, response, validateResult))
			return;

		String[] phones = request(request, "phones").split(",");
		List<String> iphones = new ArrayList<String>();
		for (String phone : phones) {
			iphones.add(phone);
		}
		JSONUtils
				.SUCCESS(response, userLoginService.queryUserByPhones(iphones));
	}

	/**
	 * 通过群id获取聊天群用户
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("getChatGroupUsers")
	public void getChatGroupUsers(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, String> validateResult = new HashMap<String, String>();
		validateResult.put("groupId", "环信聊天群id不能为空.");
		if (!validateParamBlank(request, response, validateResult))
			return;

		String groupId = request(request, "groupId");
		ResponseWrapper responseWrapper = EmchatOperator
				.getChatGroupUsers(groupId);
		if (responseWrapper.hasError()) {
			JSONUtils.ERROR(response, responseWrapper.toString());
			return;
		}

		ObjectNode objectNode = (ObjectNode) responseWrapper.getResponseBody();
		JsonNode data = objectNode.get("data");
		JSONArray array = JSON.parseArray(data.toString());
		List<String> phones = new ArrayList<String>();
		for (Object obj : array) {
			JSONObject json = (JSONObject) obj;
			if (json.containsKey("owner")) {
				phones.add(json.getString("owner"));
			} else if (json.containsKey("member")) {
				phones.add(json.getString("member"));
			}
		}
		JSONUtils.SUCCESS(response, userLoginService.queryUserByPhones(phones));
	}

	/**
	 * MD5加密
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("encodePassWord")
	public void encodePassWord(HttpServletRequest request,
			HttpServletResponse response) {
		String password = request.getParameter("password");
		if (StringUtils.isBlank(password)) {
			JSONUtils.PARAM_ERROR(response, "传入密码不能为空");
			return;
		}

		JSONUtils.SUCCESS(response, MD5Util.encodeToMD5(password));
	}

	/**
	 * 获取用户注册验证码
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("getRegistCode")
	public void getRegistCode(HttpServletRequest request,
			HttpServletResponse response) {
		String phone = request.getParameter("phone");
		String reset = request.getParameter("reset");// 是否重置
		if (StringUtils.isBlank(phone)) {
			JSONUtils.PARAM_ERROR(response, "手机号不能为空.");
			return;
		}

		if (StringUtils.isNotBlank(reset) && "true".equals(reset)) {
			// 随机参数6位数验证码
			this.sendVerifyCode(request, response, phone);
			return;
		}

		// 检查是否已注册并且已付款
		if (this.verifyRegist(phone)) {
			JSONUtils.ERROR(response, "当前用户已注册且已付款,不能重复重复获取验证码.");
			return;
		}
		// 随机参数6位数验证码
		this.sendVerifyCode(request, response, phone);
	}

	/**
	 * 发送验证码
	 * 
	 * @param request
	 * @param response
	 * @param phone
	 */
	private void sendVerifyCode(HttpServletRequest request,
			HttpServletResponse response, String phone) {
		int code = LoginUtil.generatePhoneCode(request, phone);
		JSONUtils.SUCCESS(response, code);
	}

	/**
	 * 通过手机查询当前用户
	 * 
	 * @return
	 */
	private List<User> queryUserByPhone(String phone) {
		User user = new User();
		user.setPhone(phone);
		user.setIspay(null);
		return userLoginService.queryUserByCondition(user);
	}

	/**
	 * 判断当前用户是否已注册
	 * 
	 * @return
	 */
	private boolean isRegist(String phone) {
		User user = new User();
		user.setPhone(phone);
		return userLoginService.isRegist(user);
	}

	/**
	 * 校验当前用户是否已注册并且已付款
	 * 
	 * @param phone
	 * @return
	 */
	private boolean verifyRegist(String phone) {
		User user = new User();
		user.setPhone(phone);
		return userLoginService.verifyRegist(user);
	}

	/**
	 * 获取邀请码(同时需要对接环信创建聊天室)
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("getInviteCode")
	public void getInviteCode(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, String> validateResult = new HashMap<String, String>();
		validateResult.put("userId", "用户id不能为空.");
		if (!validateParamBlank(request, response, validateResult))
			return;

		String userId = request(request, "userId");
		List<InviteCode> lst = inviteCodeService.queryInviteCode(userId);
		if (lst.size() > 0) {
			JSONUtils.SUCCESS(response, lst.get(0).getNumber());
			return;
		}
	}

	/**
	 * 创建邀请码和生成聊天室
	 * 
	 * @param request
	 * @return 邀请码
	 */
	private String createInviteCode(HttpServletRequest request) {
		User user = getCurrentUser(request);
		String desc = "[" + user.getPhone() + "]新建群";// 群描述
		// 创建自己的聊天室
		ResponseWrapper responseWrapper = EmchatOperator.createChatGroup(
				"新建聊天群", desc, user.getPhone(),
				new String[] { user.getPhone() });
		ObjectNode node = (ObjectNode) responseWrapper.getResponseBody();
		String roomId = node.get("data").get("groupid").asText();
		logger.info("roomId:" + roomId);

		// 生成邀请码
		InviteCode code = new InviteCode();
		code.setNumber(String.valueOf(LoginUtil.getSixCode()));// 邀请码
		code.setChatroomId(roomId);
		code.setUserId(user.getId());
		inviteCodeService.insert(code);

		// 修改聊天室名称为邀请码
		EmchatOperator.modifyChatGroup(roomId, user.getPhone() + "的徒弟", desc
				+ "_" + code.getNumber(), user.getPhone(),
				new String[] { user.getPhone() });
		return code.getNumber();
	}

	/**
	 * 重置密码
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("resetPassword")
	public void resetPassword(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, String> validateResult = new HashMap<String, String>();
		validateResult.put("phone", "手机号码不能为空.");
		validateResult.put("code", "手机验证码不能为空.");
		validateResult.put("password", "密码不能为空.");
		validateResult.put("repeatpwd", "重复密码不能为空.");
		if (!validateParamBlank(request, response, validateResult))
			return;

		String phone = request(request, "phone");
		String code = request(request, "code");
		String password = request(request, "password");
		String repeatpwd = request(request, "repeatpwd");

		if (!isRegist(phone)) {
			JSONUtils.ERROR(response, "当前用户尚未注册,不能找回密码.");
			return;
		}

		if (!password.equals(repeatpwd)) {
			JSONUtils.PARAM_ERROR(response, "两次的密码不一致.");
			return;
		}

		if (!code.equals(request.getSession().getAttribute("code"))) {
			JSONUtils.ERROR(response, "验证码已失效,请重新发送验证码.");
			return;
		}

		User user = new User();
		user.setPassword(password);
		UserExample example = new UserExample();
		example.or().andPhoneEqualTo(phone);
		if (userLoginService.updateByExampleSelective(user, example)) {
			JSONUtils.SUCCESS(response, "修改密码成功.");
		} else {
			JSONUtils.ERROR(response, "修改密码失败.");
		}
	}

	/**
	 * 获取重置的验证码
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("getResetCode")
	public void getResetCode(HttpServletRequest request,
			HttpServletResponse response) {
		String phone = request(request, "phone");
		Map<String, String> validateResult = new HashMap<String, String>();
		validateResult.put("phone", "手机号码不能为空.");
		if (!validateParamBlank(request, response, validateResult))
			return;

		// 随机参数6位数验证码
		int code = LoginUtil.generatePhoneCode(request, phone);
		JSONUtils.SUCCESS(response, code);
	}

	/**
	 * 查询剩余会员天数
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("queryMemberDays")
	public void queryMemberDays(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, String> validateResult = new HashMap<String, String>();
		validateResult.put("userId", "手机号码不能为空.");
		if (!validateParamBlank(request, response, validateResult))
			return;

		String userId = request(request, "userId");
		int days = userLoginService.queryMemberDays(Integer.parseInt(userId));
		JSONUtils.SUCCESS(response, days);
	}

	/**
	 * 保存聊天记录
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping("saveChatRecord")
	public void saveChatRecord(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, String> validateResult = new HashMap<String, String>();
		validateResult.put("userId", "用户id不能为空");
		validateResult.put("content", "聊天内容不能为空");
		validateResult.put("fromId", "环信聊天发起人不能空");
		validateResult.put("toId", "环信聊天接收人不能为空");
		if (!validateParamBlank(request, response, validateResult))
			return;

		ChatRecord chatRecord = BeanUtils.mapConvert2ToBean(ChatRecord.class,
				request);
		chatRecord.setCreateTime(new Date());
		userLoginService.saveChatRecord(chatRecord);
		JSONUtils.SUCCESS(response, chatRecord.getId());
	}

}
