package cz.muni.fi.pa165.cards.servlets;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpRequestHandler;

import cz.muni.fi.pa165.cards.db.Image;
import cz.muni.fi.pa165.cards.managers.ImageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("imageServletFromDatabase")
public class ImageServletFromDatabase implements HttpRequestHandler {
        private static final Logger log = LoggerFactory.getLogger(ImageServletFromDatabase.class);

	@Autowired
	private ImageManager imageManager;
	
	private static final int DEFAULT_BUFFER_SIZE = 10240;

	@Override
	public void handleRequest(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {

		Long idOfImage = Long.valueOf(request.getPathInfo().substring(1));

		System.err.println("######## Servlet get request to render image with id: "
						+ idOfImage);
		
		Image image = imageManager.getImage(idOfImage);
		
		byte[] picture = null;
		String name= null;
		
		if ( image == null ) {
			
			name = "noPhotoSmall.jpg";
			
			if(idOfImage == -2L) {
				name = "noPhotoMedium.jpg";
			} 
			
			picture = IOUtils.toByteArray(
	                new FileInputStream(new File(request.getSession().getServletContext().getRealPath("resources/img/" + name))));
		} else {
			picture = image.getPicture();
			name = image.getName();
		}

		response.setContentType("image/jpeg");
		response.setContentLength(picture.length);

		response.setHeader("Content-Disposition", "inline; filename=\"" + name
				+ "\"");

		BufferedInputStream input = null;
		BufferedOutputStream output = null;

		try {
			input = new BufferedInputStream(new ByteArrayInputStream(picture));
			output = new BufferedOutputStream(response.getOutputStream());
			byte[] buffer = new byte[8192];
			int length;
			while ((length = input.read(buffer)) > 0) {
				output.write(buffer, 0, length);
			}
		} catch (IOException e) {
			System.out
					.println("There are errors in reading/writing image stream "
							+ e.getMessage());
		} finally {
			if (output != null)
				try {
					output.close();
				} catch (IOException ignore) {
				}
			if (input != null)
				try {
					input.close();
				} catch (IOException ignore) {
				}
		}

	}

}
