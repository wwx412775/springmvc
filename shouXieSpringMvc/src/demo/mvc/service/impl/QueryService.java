package demo.mvc.service.impl;

import annotation.GPService;
import demo.mvc.service.IQueryService;
@GPService
public class QueryService implements IQueryService {

	public String search(String name) {
		return "invork search name="+ name;
	}
}
