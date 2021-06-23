package org.joget.tutorial;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.ArrayUtils;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListActionDefault;
import org.joget.apps.datalist.model.DataListActionResult;
import org.joget.apps.form.service.FormPdfUtil;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;

public class DownloadPdfDatalistAction extends DataListActionDefault {
    
    private final static String MESSAGE_PATH = "messages/DownloadPdfDatalistAction";

    @Override
    public String getName() {
        return "Download PDF Datalist Action";
    }

    @Override
    public String getVersion() {
        return "7.0.0";
    }
    
    @Override
    public String getClassName() {
        return getClass().getName();
    }
    
    @Override
    public String getLabel() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.tutorial.DownloadPdfDatalistAction.pluginLabel", getClassName(), MESSAGE_PATH);
    }
    
    @Override
    public String getDescription() {
        //support i18n
        return AppPluginUtil.getMessage("org.joget.tutorial.DownloadPdfDatalistAction.pluginDesc", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/downloadPdfDatalistAction.json", null, true, MESSAGE_PATH);
    }

    @Override
    public String getLinkLabel() {
        return getPropertyString("label"); //get label from configured properties options
    }

    @Override
    public String getHref() {
        return getPropertyString("href"); //Let system to handle to post to the same page
    }

    @Override
    public String getTarget() {
        return "post";
    }

    @Override
    public String getHrefParam() {
        return getPropertyString("hrefParam");  //Let system to set the parameter to the checkbox name
    }

    @Override
    public String getHrefColumn() {
        String recordIdColumn = getPropertyString("recordIdColumn"); //get column id from configured properties options
        if ("id".equalsIgnoreCase(recordIdColumn) || recordIdColumn.isEmpty()) {
            return getPropertyString("hrefColumn"); //Let system to set the primary key column of the binder
        } else {
            return recordIdColumn;
        }
    }

    @Override
    public String getConfirmation() {
        return getPropertyString("confirmation"); //get confirmation from configured properties options
    }

    @Override
    public DataListActionResult executeAction(DataList dataList, String[] rowKeys) {
        // only allow POST
        HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
        if (request != null && !"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        
        // check for submited rows
        if (rowKeys != null && rowKeys.length > 0) {
            try {
                //get the HTTP Response
                HttpServletResponse response = WorkflowUtil.getHttpServletResponse();

                if (rowKeys.length == 1) {
                    //generate a pdf for download
                    singlePdf(request, response, rowKeys[0]);
                } else {
                    //generate a zip of all pdfs
                    multiplePdfs(request, response, rowKeys);
                }
            } catch (IOException | ServletException e) {
                LogUtil.error(getClassName(), e, "Fail to generate PDF for " + ArrayUtils.toString(rowKeys));
            }
        }
        
        //return null to do nothing
        return null;
    }
    
    /**
     * Handles for single pdf file
     * @param request
     * @param response
     * @param rowKey
     * @throws IOException 
     * @throws javax.servlet.ServletException 
     */
    protected void singlePdf(HttpServletRequest request, HttpServletResponse response, String rowKey) throws IOException, ServletException {
        byte[] pdf = getPdf(rowKey);
        writeResponse(request, response, pdf, rowKey+".pdf", "application/pdf");
    }
    
    /**
     * Handles for multiple files download. Put all pdfs in zip.
     * @param request
     * @param response
     * @param rowKeys 
     * @throws java.io.IOException 
     * @throws javax.servlet.ServletException 
     */
    protected void multiplePdfs(HttpServletRequest request, HttpServletResponse response, String[] rowKeys) throws IOException, ServletException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(baos);
        
        try {
            //create pdf and put in zip
            for (String id : rowKeys) {
                byte[] pdf = getPdf(id);
                zip.putNextEntry(new ZipEntry(id+".pdf"));
                zip.write(pdf);
                zip.closeEntry();
            }

            zip.finish();
            writeResponse(request, response, baos.toByteArray(), getLinkLabel() +".zip", "application/zip");
        } finally {
            baos.close();
            zip.flush();
        }
    }
    
    /**
     * Generate PDF using FormPdfUtil
     * @param id
     * @return 
     */
    protected byte[] getPdf(String id) {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String formDefId = getPropertyString("formDefId");

        Boolean hideEmptyValueField = null;
        if (getPropertyString("hideEmptyValueField").equals("true")) {
            hideEmptyValueField = true;
        }
        
        Boolean showNotSelectedOptions = false;
        if (getPropertyString("showNotSelectedOptions").equals("true")) {
            showNotSelectedOptions = true;
        }
        Boolean repeatHeader = null;
        if ("true".equals(getPropertyString("repeatHeader"))) {
            repeatHeader = true;
        }
        Boolean repeatFooter = null;
        if ("true".equals(getPropertyString("repeatFooter"))) {    
            repeatFooter = true;
        }
        String css = null;
        if (!getPropertyString("formatting").isEmpty()) {
            css = getPropertyString("formatting");
        }
        String header = null; 
        if (!getPropertyString("headerHtml").isEmpty()) {
            header = getPropertyString("headerHtml");
            header = AppUtil.processHashVariable(header, null, null, null);
        }

        String footer = null; 
        if (!getPropertyString("footerHtml").isEmpty()) {
            footer = getPropertyString("footerHtml");
            footer = AppUtil.processHashVariable(footer, null, null, null);
        }
        
        return FormPdfUtil.createPdf(formDefId, id, appDef, null, hideEmptyValueField, header, footer, css, showNotSelectedOptions, repeatHeader, repeatFooter);
    }
    
    /**
     * Write to response for download
     * @param request
     * @param response
     * @param bytes
     * @param filename
     * @param contentType
     * @throws IOException 
     */
    protected void writeResponse(HttpServletRequest request, HttpServletResponse response, byte[] bytes, String filename, String contentType) throws IOException, ServletException {
        OutputStream out = response.getOutputStream();
        try {
            String name = URLEncoder.encode(filename, "UTF8").replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename="+name+"; filename*=UTF-8''" + name);
            response.setContentType(contentType+"; charset=UTF-8");
            
            if (bytes.length > 0) {
                response.setContentLength(bytes.length);
                out.write(bytes);
            }
        } finally {
            out.flush();
            out.close();
            
            //simply foward to a 
            request.getRequestDispatcher(filename).forward(request, response);
        }
    }
}
