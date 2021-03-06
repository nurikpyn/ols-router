package ca.bc.gov.ols.router.admin;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.google.gson.stream.JsonWriter;

import ca.bc.gov.ols.router.admin.data.Configuration;

@RestController
public class AdminController {
	static final Logger logger = LoggerFactory.getLogger(
			AdminController.class.getCanonicalName());
	
	@Autowired
	private AdminApplication adminApp;
	
	@GetMapping(value = "/export", produces = "application/json")
	public void doExport(HttpServletResponse response) throws IOException {
		Session session = adminApp.getSession();
		String keyspace = adminApp.getKeyspace();
		
		// export all of the data in the database
		response.addHeader("Content-Type", "application/json");
		response.addHeader("Content-Disposition", "attachment; filename=ols_admin_config_export.json");
		response.setCharacterEncoding("UTF-8");
		JsonWriter jw = new JsonWriter(response.getWriter());
		jw.setIndent("  ");
		jw.beginObject();
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		jw.name("exportDate").value(dateFormat.format(new Date()));
		ResultSet rs;
		int rowCount;
		
		// BGEO_CONFIGURATION_PARAMETERS
		jw.name("BGEO_CONFIGURATION_PARAMETERS");
		jw.beginObject();
		rs = session.execute("SELECT app_id, config_param_name, config_param_value FROM " + keyspace + ".BGEO_CONFIGURATION_PARAMETERS WHERE app_id = 'ROUTER'");
		jw.name("rows");
		jw.beginArray();
		rowCount = 0;
		for (Row row : rs) {
			rowCount++;
			jw.beginObject();
			jw.name("app_id").value(row.getString("app_id"));
			jw.name("config_param_name").value(row.getString("config_param_name"));
			jw.name("config_param_value").value(row.getString("config_param_value"));
			jw.endObject();
		}
		jw.endArray();
		jw.name("rowCount").value(rowCount);
		jw.endObject();		

		jw.endObject();
		jw.flush();
		jw.close();
	}
	
	@PostMapping(value = "/validate")
	public ModelAndView doValidate(@RequestParam("file") MultipartFile file) {
		Configuration conf = new Configuration(file);
		conf.compare(adminApp);
		return new ModelAndView("view/validate", "configuration", conf);
	}

	@PostMapping(value = "/import"	)
	public ModelAndView doImport(@RequestParam("file") MultipartFile file) {
		Configuration conf = new Configuration(file);
		if(conf.getErrors().isEmpty()) {
			adminApp.save(conf);
		}
		return new ModelAndView("view/import", "configuration", conf);
	}
	
}
