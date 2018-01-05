package demo.mvc.service.impl;

import annotation.GPService;
import demo.mvc.service.IModifyService;
@GPService
public class ModifyService implements IModifyService {


	public String add(String name, String addr) {
		return "invork add name="+ name+",addr"+addr;
	}

	public String remove(Integer id) {		
		return "invork remove id="+ id;
	}
	
	

}
