package com.nvault.s3.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.nvault.doc.dvo.UserDocDVO;
import com.nvault.model.NVaultUser;
import com.nvault.s3.model.S3Bucket;
import com.nvault.s3.model.S3Folder;
import com.nvault.s3.service.S3BucketService;

@RestController
public class S3BucketController {

	@Autowired
	public Environment env;
	@Autowired
	public S3BucketService bucketService;

	/**
	 * @param userMap
	 * @return This method is used to create bucket when user is registered for
	 *         the first time.
	 */
	@RequestMapping(value = "/createBucket", method = RequestMethod.POST, produces = MediaType.TEXT_HTML_VALUE)
	public ResponseEntity<String> createBucket(@RequestBody HashMap<String, String> userMap) {
		try {
			new Thread(new Runnable() {
				public void run() {
					creationProcess(userMap.get("bucketName"), userMap.get("userName"));
				}
			}).start();
		} catch (Exception e) {
			System.out.println("Exception Occured in bucketCreation" + e.getMessage());
		}
		return new ResponseEntity<String>("Successfully created", HttpStatus.CREATED);

	}

	/**
	 * @param folderName
	 * @return This Method is used to fetch the folders and files which includes
	 *         in the given Folder Name.
	 */
	@RequestMapping(value = "/fetchDocs", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	// Need to write logic for fetching the Folders.
	public ResponseEntity<List<List<UserDocDVO>>> getDocs(@RequestParam("folderName") String folderName) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		NVaultUser user = (NVaultUser) auth.getPrincipal();
		S3Bucket bucket = bucketService.findByuserName(user.getUsername());
		AWSCredentials credentials = new BasicAWSCredentials(env.getProperty("accessKey"),
				env.getProperty("securityKey"));
		AmazonS3 s3Client = new AmazonS3Client(credentials);
		ListObjectsRequest listObjectsRequest = new ListObjectsRequest().withBucketName(bucket.getBucketName())
				.withPrefix(folderName + "/");
		ObjectListing objects = s3Client.listObjects(listObjectsRequest);
		List<UserDocDVO> filesList = new ArrayList<UserDocDVO>();
		List<UserDocDVO> foldersList = new ArrayList<UserDocDVO>();
		List<List<UserDocDVO>> allList = new ArrayList<List<UserDocDVO>>();
		List<S3ObjectSummary> list = objects.getObjectSummaries();
		List<S3Folder> listFolders = bucketService.listAllFolders(folderName + "/");
		for (S3ObjectSummary summary : list) {
			if ((!summary.getKey().endsWith("/"))) {
				UserDocDVO userDocDVO = new UserDocDVO();
				userDocDVO.setFileName(summary.getKey().substring(summary.getKey().lastIndexOf("/") + 1));
				userDocDVO.setModifiedDate(summary.getLastModified());
				userDocDVO.setSize(summary.getSize() / 1024);
				userDocDVO.setFileType("file");
				filesList.add(userDocDVO);
			}
		}
		for (S3Folder s3Folder : listFolders) {
			UserDocDVO userDocDVO = new UserDocDVO();
			userDocDVO.setFileName(s3Folder.getFolderName());
			userDocDVO.setFileType("folder");
			foldersList.add(userDocDVO);
		}
		allList.add(0, foldersList);
		allList.add(1, filesList);
		System.out.println("userDocs" + allList);
		return new ResponseEntity<List<List<UserDocDVO>>>(allList, HttpStatus.CREATED);
	}

	/**
	 * @param f
	 * @return
	 * @throws IOException
	 *             This is used to upload the docs in the Home Folder. Need to
	 *             add the logic for upload the folder into the selected folder.
	 */
	@RequestMapping(value = "/uploadDocs", method = RequestMethod.POST, produces = MediaType.TEXT_HTML_VALUE)
	public ResponseEntity<String> uploadDocs(@RequestParam("file") MultipartFile f) throws IOException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		NVaultUser user = (NVaultUser) auth.getPrincipal();
		S3Bucket bucket = bucketService.findByuserName(user.getUsername());
		AmazonS3 s3Client = new AmazonS3Client(
				new BasicAWSCredentials(env.getProperty("accessKey"), env.getProperty("securityKey")));
		if (bucket != null) {
			try {
				InputStream stream = new ByteArrayInputStream(f.getBytes());
				ObjectMetadata meta = new ObjectMetadata();
				meta.setContentLength(f.getSize());
				s3Client.putObject(bucket.getBucketName(), "home/" + f.getOriginalFilename(), stream, meta);
				return new ResponseEntity<String>("File Uploaded SuccessFully", HttpStatus.CREATED);
				
			} catch (Exception e) {
				System.out.println("Exception occured in uploading file" + e.getMessage());
				return new ResponseEntity<String>("File is not Uploaded", HttpStatus.BAD_REQUEST);
			}
		} else {
			creationProcess(user.getMail().split("@")[0], user.getUsername());
			InputStream stream = new ByteArrayInputStream(f.getBytes());
			ObjectMetadata meta = new ObjectMetadata();
			meta.setContentLength(f.getSize());
			s3Client.putObject(bucket.getBucketName(), "home/" + f.getOriginalFilename(), stream, meta);
			return new ResponseEntity<String>("File Uploaded SuccessFully", HttpStatus.CREATED);
		}

	}

	/**
	 * @param fileNames
	 * @param folderName
	 * @return This is used to move the docs from one folder to trash/archive.
	 */
	@RequestMapping(value = "/updateDocs", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
	public ResponseEntity<List<String>> updateDocs(@RequestParam("fileNames") List<String> fileNames,
			@RequestParam("folderName") String folderName) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		NVaultUser user = (NVaultUser) auth.getPrincipal();
		S3Bucket bucket = bucketService.findByuserName(user.getUsername());
		List<String> resultMap = new ArrayList<String>();
		for (String fileName : fileNames) {
			String status = deletionProcess(fileName, bucket.getBucketName(), folderName);
			if ("success".equalsIgnoreCase(status)) {
				resultMap.add(fileName+" SuccessFully Moved to " + folderName+" \n");
			} else {
				resultMap.add(fileName+" Not SuccessFully Moved to " + folderName+" \n");
			}
		}
		return new ResponseEntity<List<String>>(resultMap, HttpStatus.CREATED);
	}

	/**
	 * @param folderMap
	 * @return This is used to create a folder in the specific path.
	 */
	@RequestMapping(value = "/createFolder", method = RequestMethod.POST, produces = MediaType.TEXT_HTML_VALUE)
	public ResponseEntity<String> createFolder(@RequestBody HashMap<String, String> folderMap) {
		AWSCredentials credentials = new BasicAWSCredentials(env.getProperty("accessKey"),
				env.getProperty("securityKey"));
		AmazonS3 s3Client = new AmazonS3Client(credentials);
		String baseFolderName = folderMap.get("baseFolderName");
		String newFolderName = folderMap.get("newFolderName");
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		NVaultUser user = (NVaultUser) auth.getPrincipal();
		S3Bucket bucket = bucketService.findByuserName(user.getUsername());
		S3Folder s3Folder = new S3Folder();
		s3Folder.setBucketName(bucket.getBucketName());
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(0);
		InputStream emptyContent = new ByteArrayInputStream(new byte[0]);
		PutObjectRequest putObjectRequest = null;
		String status = "success";
		try {
			if (baseFolderName != null && baseFolderName != "") {
				putObjectRequest = new PutObjectRequest(bucket.getBucketName() + "/home/" + baseFolderName,
						newFolderName + "/", emptyContent, metadata);
				s3Folder.setBaseFolder("home/" + baseFolderName);
				s3Folder.setFolderName(newFolderName);
			} else {
				putObjectRequest = new PutObjectRequest(bucket.getBucketName() + "/home", newFolderName + "/",
						emptyContent, metadata);
				s3Folder.setBaseFolder("home/");
				s3Folder.setFolderName(newFolderName);
			}
			s3Client.putObject(putObjectRequest);
			bucketService.saveFolder(s3Folder);
		} catch (Exception e) {
			System.out.println("Exception occured while creating folder" + e.getMessage());
			status = "failure";
		}
		if ("success".equalsIgnoreCase(status)) {
			return new ResponseEntity<String>("Folder is Successfully created", HttpStatus.OK);
		} else {
			return new ResponseEntity<String>("Folder is Not Created Successfully", HttpStatus.BAD_REQUEST);
		}
	}

	/**
	 * @param bucketName
	 * @param userName
	 *            This Method is used for creation of Bucket in Amazon s3.
	 */
	public void creationProcess(String bucketName, String userName) {
		S3Bucket bucket = new S3Bucket();
		bucket.setBucketName(bucketName);
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(0);
		AmazonS3 s3Client = new AmazonS3Client(
				new BasicAWSCredentials(env.getProperty("accessKey"), env.getProperty("securityKey")));
		try {
			s3Client.createBucket(bucketName);
			bucket.setUserName(userName);
		} catch (Exception e) {
			bucket = null;
			System.out.println("bucket already Exists" + e.getMessage());
		}
		try {
			// create empty content
			InputStream emptyContent = new ByteArrayInputStream(new byte[0]);
			// create a PutObjectRequest passing the folder name suffixed by
			// /
			PutObjectRequest homeObjReq = new PutObjectRequest(bucketName, env.getProperty("homePath"), emptyContent,
					metadata);
			S3Folder homeFolder = new S3Folder();
			homeFolder.setBucketName(bucketName);
			homeFolder.setBaseFolder(null);
			homeFolder.setFolderName(env.getProperty("homePath"));
			PutObjectRequest trashObjReq = new PutObjectRequest(bucketName, env.getProperty("trashPath"), emptyContent,
					metadata);
			S3Folder trashFolder = new S3Folder();
			trashFolder.setBucketName(bucketName);
			trashFolder.setBaseFolder(null);
			trashFolder.setFolderName(env.getProperty("trashPath"));
			PutObjectRequest archiveObjReq = new PutObjectRequest(bucketName, env.getProperty("archivePath"),
					emptyContent, metadata);
			S3Folder archiveFolder = new S3Folder();
			archiveFolder.setBucketName(bucketName);
			archiveFolder.setBaseFolder(null);
			archiveFolder.setFolderName(env.getProperty("archivePath"));
			// send request to S3 to create folder
			s3Client.putObject(homeObjReq);
			s3Client.putObject(trashObjReq);
			s3Client.putObject(archiveObjReq);
			bucket.setUserName(userName);
			bucketService.saveBucket(bucket);
			bucketService.saveFolder(homeFolder);
			bucketService.saveFolder(trashFolder);
			bucketService.saveFolder(archiveFolder);
		} catch (Exception e) {
			bucket = null;
			System.out.println("bucket already Exists" + e.getMessage());
		}

	}

	/**
	 * @param fileName
	 * @param file
	 * @param bucketName
	 *            This method is used to move the Document to Trash/Archive.
	 */
	public String deletionProcess(String fileName, String bucketName, String folderName) {
		System.out.println(folderName + "/" + fileName);
		AmazonS3 s3Client = new AmazonS3Client(
				new BasicAWSCredentials(env.getProperty("accessKey"), env.getProperty("securityKey")));
		try {
			// Need to get the File from Home Location.
			S3Object object = s3Client.getObject(new GetObjectRequest(bucketName + "/home", fileName));
			InputStream is = object.getObjectContent();
			s3Client.putObject(bucketName, folderName + "/" + fileName, is, object.getObjectMetadata());
			s3Client.deleteObject(bucketName + "/home", fileName);
			return "success";
		} catch (Exception e) {
			return "failure";
		}

	}

	@RequestMapping(value = "/downloadDoc", method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
	public void downloadDocs(@RequestParam("fileName") String fileName, @RequestParam("folderName") String folderName,
			HttpServletResponse response) throws IOException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		NVaultUser user = (NVaultUser) auth.getPrincipal();
		S3Bucket bucket = bucketService.findByuserName(user.getUsername());
		AmazonS3 s3Client = new AmazonS3Client(
				new BasicAWSCredentials(env.getProperty("accessKey"), env.getProperty("securityKey")));
		S3Object object = s3Client.getObject(new GetObjectRequest(bucket.getBucketName() + "/" + folderName, fileName));
		InputStream is = object.getObjectContent();

		// MIME type of the file
		response.setContentType("application/octet-stream");
		// Response header
		response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
		// Read from the file and write into the response
		OutputStream os = response.getOutputStream();
		byte[] buffer = new byte[1024];
		int len;
		while ((len = is.read(buffer)) != -1) {
			os.write(buffer, 0, len);
		}
		os.flush();
		os.close();
		is.close();
	}

	@RequestMapping(value = "/shareDownload/", method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
	public String downloadPDFFile(@RequestParam String filename) throws IOException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		NVaultUser user = (NVaultUser) auth.getPrincipal();
		System.out.println(filename);
		String userBucket = user.getUsername().toLowerCase() + "" + user.getId();
		System.out.println(userBucket);
		AmazonS3 s3Client = new AmazonS3Client(
				new BasicAWSCredentials(env.getProperty("accessKey"), env.getProperty("securityKey")));
		S3Object object = s3Client.getObject(new GetObjectRequest(userBucket + "/home", filename));
		String bucketName = userBucket;
		String objectKey = object.getKey();
		GeneratePresignedUrlRequest generateUrl = new GeneratePresignedUrlRequest(bucketName, "home/" + objectKey);
		generateUrl.setMethod(HttpMethod.GET); // Default.
		Calendar cal = Calendar.getInstance();
		cal.setTime(new java.util.Date());
		cal.add(Calendar.DATE, 2);
		generateUrl.setExpiration(cal.getTime());
		URL url = s3Client.generatePresignedUrl(generateUrl);
		return url.toString();
	}

}
