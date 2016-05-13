package be.nabu.libs.resources.remote.server;

import java.util.Date;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "entry")
@XmlType(propOrder = { "name", "contentType", "size", "lastModified", "path", "children" })
public class Entry {
	private String name, path;
	private String contentType;
	private Long size;
	private Date lastModified;
	private Listing children;

	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getContentType() {
		return contentType;
	}
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}
	public Long getSize() {
		return size;
	}
	public void setSize(Long size) {
		this.size = size;
	}
	public Date getLastModified() {
		return lastModified;
	}
	public void setLastModified(Date lastModified) {
		this.lastModified = lastModified;
	}
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
	public Listing getChildren() {
		return children;
	}
	public void setChildren(Listing children) {
		this.children = children;
	}
}
