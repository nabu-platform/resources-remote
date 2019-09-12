package be.nabu.libs.resources.remote.server;

import java.util.Date;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "entry")
@XmlType(propOrder = { "name", "hash", "contentType", "size", "lastModified", "path", "children", "content" })
public class Entry {
	private String name, path, hash;
	private String contentType;
	private Long size;
	private Date lastModified;
	private Listing children;
	private byte [] content;

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
	public byte[] getContent() {
		return content;
	}
	public void setContent(byte[] content) {
		this.content = content;
	}
	public String getHash() {
		return hash;
	}
	public void setHash(String hash) {
		this.hash = hash;
	}
}
