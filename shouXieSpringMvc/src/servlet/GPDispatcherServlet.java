package servlet;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.tribes.util.Arrays;

import com.sun.corba.se.impl.encoding.OSFCodeSetRegistry.Entry;
import com.sun.corba.se.pept.transport.ContactInfo;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.Annotation;
import com.sun.xml.internal.ws.util.StringUtils;

import annotation.GPAutowired;
import annotation.GPController;
import annotation.GPRequestMapping;
import annotation.GPRequestParam;
import annotation.GPService;

public class GPDispatcherServlet extends HttpServlet{
	private static final long serialVersionUID = 1L;
	private List<String> classNames  = new ArrayList<String>();
	private List<Handler> handlerMapping = new ArrayList<Handler>();
	private Map<String,Object> instanceMapping = new HashMap<String, Object>();
//	private Map<Pattern,Handler> handlerMapping = new HashMap<Pattern,Handler>();
	
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {		
		doPost(req, resp);
	}
	
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			// 以上已经初始化的信息匹配
			boolean isMatcher = pattern(req, resp);
			if (!isMatcher) {
				resp.getWriter().write("404 not Found");
			}
		} catch (Exception e) {
			resp.getWriter().write("500 Execption,Detail:\r\n" + e.getMessage());
		}
	}

	public boolean pattern(HttpServletRequest req, HttpServletResponse resp)  throws Exception {
		if (handlerMapping.isEmpty()) {
		   return false;
		}
		String url = req.getRequestURI();
		String contextPath = req.getContextPath();
		url = url.replace(contextPath, "").replace("/+", "");
		
	    for ( Handler handler : handlerMapping) {
			try {
				Matcher matcher = handler.pattern.matcher(url);
				if (!matcher.matches()) { continue;}				
					Class<?> [] paramTypes = handler.method.getParameterTypes();
					Object [] paramValues = new Object [paramTypes.length];
					Map<String, String[]> params = req.getParameterMap();
					for (java.util.Map.Entry<String, String []> param : params.entrySet()) {
						 String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\[", "").replaceAll(",\\s", ",");
						 if (!handler.paramMapping.containsKey(param.getKey())) {continue;}
						 int index = handler.paramMapping.get(param.getKey());
						 paramValues[index] = castStringValue(value, paramTypes[index]);
					}
					int reqIndex = handler.paramMapping.get(HttpServletRequest.class.getName());
					paramValues[reqIndex] = req;
					int repIndex = handler.paramMapping.get(HttpServletResponse.class.getName());
					paramValues[repIndex] = resp;					
					handler.method.invoke(handler.controller, paramValues);
					return true;
			}catch(Exception e){
				throw e;
			}
		}
		
		return false;
	}

	public void init(ServletConfig config) throws ServletException {
		//读取配置文件
		String scanPackage=config.getInitParameter("scanPackage");
		//扫
		scanClass(scanPackage);		
		//把扫描出来的类进行实例化
		instance();		
		//建立依赖关系，自动依赖注入
		autowired();		
		//建立url和method的映射关系
		handlerMpping();		
		//输出一句话
		System.out.println("GP MVC Framework 已经准备就绪了，欢迎使用");
	}
	
	private void handlerMpping() {
		if (instanceMapping.isEmpty()) {
			return;
		}
		for (java.util.Map.Entry<String, Object> entry : instanceMapping.entrySet()) {
			Class<?> clazz= entry.getValue().getClass();
			if (!clazz.isAnnotationPresent(GPController.class)) {
				continue;
			}
			String url = "";
			if (clazz.isAnnotationPresent(GPRequestMapping.class)) {
				GPRequestMapping requestMapping = clazz.getAnnotation(GPRequestMapping.class);
				url = requestMapping.value();
			}
			
			Method [] methods = clazz.getMethods();
			for (Method method : methods) {
				if (!method.isAnnotationPresent(GPRequestMapping.class)) { continue;}				
				GPRequestMapping requestMapping = method.getAnnotation(GPRequestMapping.class);
				String regex =  url + requestMapping.value();
//			    String regex = "/" + url + requestMapping.value();	
			    regex = regex.replace("/+", "/").replaceAll("\\*", ".*");
			    Map<String, Integer> pm = new HashMap<String, Integer>();
			    java.lang.annotation.Annotation[][] pa = method.getParameterAnnotations();
			    for(int i = 0; i<pa.length; i++) {
			    	for(java.lang.annotation.Annotation a: pa[i]) {
			    		if (a instanceof GPRequestParam) {
							String paramName = ((GPRequestParam) a).value();
							if (!"".equals(paramName.trim())) {
								pm.put(paramName, i);
							}
						}
			    	}
			    }
			    
			   Class<?>[] paramsTypes = method.getParameterTypes();
			   for(int i = 0; i<paramsTypes.length; i++) {
				   Class<?> type = paramsTypes[i];
				   if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
				       pm.put(type.getName(), i);					
				   }
			   }
/*			    handlerMapping.put(Pattern.compile(regex), new Handler(entry.getValue(),
			    		              method, pm));*/
			   handlerMapping.add(new Handler(Pattern.compile(regex), entry.getValue(),
 		              method, pm));
			    System.out.println("Mapping" + regex + "," + method);
			}			
		}
	}

	private void autowired() {
		if (instanceMapping.isEmpty()) {
			return;
		}
		for (java.util.Map.Entry<String, Object> entry : instanceMapping.entrySet()) {
	         Field[] fields=entry.getValue().getClass().getDeclaredFields();
	         for (Field field : fields) {
				if (!field.isAnnotationPresent(GPAutowired.class)) {
					continue;
				}
				GPAutowired autowired=field.getAnnotation(GPAutowired.class);
				String beanName=autowired.value().trim();
				if ("".equals(beanName)) {
				   beanName = field.getType().getName();
				}				
			    field.setAccessible(true);
			    try {
					field.set(entry.getValue(), instanceMapping.get(beanName));
				} catch (Exception e) {
					e.printStackTrace();
					continue;
				} 
			}
		}
	}

	private void instance() {
		//利用反射机制，是你胡
		if (classNames.size() == 0)
		{
			return;
		}
		for (String className : classNames) {
			try {
				Class<?> clazz = Class.forName(className);
				//找了GP
				if (clazz.isAnnotationPresent(GPController.class)) {
					String beanName = lowerFirstChar(clazz.getSimpleName());
					instanceMapping.put(beanName, clazz.newInstance());
				}else if (clazz.isAnnotationPresent(GPService.class)) {
					GPService service = clazz.getAnnotation(GPService.class);
					String beanName = service.value();
					if (!"".equals(beanName.trim())) {
						instanceMapping.put(beanName, clazz.newInstance());
						continue;
					}
					Class<?> [] interfaces = clazz.getInterfaces();
					for (Class<?> i : interfaces) {
						instanceMapping.put(i.getName(), clazz.newInstance());
					}
				}else {
					continue;
				}
			} catch (Exception e) {
				System.out.println(e.getMessage());
				continue;
			}
		}
	}

	public String lowerFirstChar(String str) {
		char [] chars= str.toCharArray();
		chars[0] += 32;
		return String.valueOf(chars);
	}

	private void scanClass(String packageName) {
		//拿到包路径，转化文件路径
		String path = "/"+ packageName.replace(".", "/");
		URL url=this.getClass().getClassLoader().getResource(path);
		File dir=new File(url.getFile());
		//递归查找到所有的class文件
		for (File file : dir.listFiles()) {
			if (file.isDirectory()) 
			{
				scanClass(packageName+"."+file.getName());
			}else
			{
				//.class
				String className=packageName + "."+file.getName().replaceAll(".class", "");
				classNames.add(className);
			}
		}
	}	
	
	private class Handler{
		protected Pattern pattern;
		protected Object controller;
		protected Method method;
		protected Map<String,Integer> paramMapping;

		protected Handler(Pattern pattern,Object controller, Method method, Map<String,Integer> paramMapping ) {
			this.pattern = pattern;
			this.controller = controller;
			this.method = method;
			this.paramMapping = paramMapping;
		}
	}
	
	private Object castStringValue(String value, Class<?> clazz) {
		if (clazz == String.class) {
			return value;
		}else if (clazz == Integer.class) {
			return Integer.valueOf(value);
		}else if (clazz == int.class) {
			return Integer.valueOf(value).intValue();
		}else {
			return null;
		}
	}
}
