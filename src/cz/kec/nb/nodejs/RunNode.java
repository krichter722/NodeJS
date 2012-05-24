/*
 * Copyright 2010 Syntea software group a.s.
 *
 * This file may be used, copied, modified and distributed only in accordance
 * with the terms of the limited licence contained in the accompanying
 * file LICENSE.TXT.
 *
 * Tento soubor muze byt pouzit, kopirovan, modifikovan a siren pouze v souladu
 * s licencnimi podminkami uvedenymi v prilozenem souboru LICENSE.TXT.
 */
package cz.kec.nb.nodejs;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.openide.awt.NotificationDisplayer;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.util.actions.CookieAction;
import org.openide.windows.IOColorLines;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;
import org.openide.windows.OutputWriter;

public final class RunNode extends CookieAction {

    public static Icon loadIcon(){
        return new ImageIcon(RunNode.class.getResource("images/win.png"));
    }
    
    private static final long serialVersionUID = -3853106497059314882L;
    private FileObject fo;
    private EditorCookie editorCookie;
    protected void performAction(Node[] activatedNodes) {
        try {
            DataObject dataObject = activatedNodes[0].getLookup().lookup(DataObject.class);
            editorCookie = activatedNodes[0].getLookup().lookup(EditorCookie.class);
            editorCookie.saveDocument();
            
            fo = dataObject.getPrimaryFile();
            fo.getURL();
            fo.getParent().getURL();
            final InputOutput io = IOProvider.getDefault().getIO("Node.js " + fo.getName(), false);
            final OutputWriter out = io.getOut();
            OutputWriter erout = io.getErr();
            io.select();
            io.setOutputVisible(true);
            out.reset();
            String command = NbPreferences.forModule(NodeJSOptionsPanel.class).get("COMMAND", "cd ${workingdir};\nnode ${selectedfile};");
            // multi OS thing
            String[] cmd;
            if(System.getProperty("os.name").toLowerCase().contains("windows")){
                String wdir = WinPath.winfixPath(fo.getParent().getURL().getPath());
                System.out.println("Working dir "+wdir);
                command = command.replaceAll("\\$\\{selectedfile\\}", fo.getNameExt());
                command = command.replaceAll("\\$\\{workingdir\\}", Matcher.quoteReplacement(wdir));
                if(command.contains(";")){
                    NotificationDisplayer.getDefault().notify("NodeJS",loadIcon(), "Windows with Unix settings detected reseting cmd options. Please run again.", null);
                    NbPreferences.forModule(NodeJSOptionsPanel.class).put("COMMAND", "cd ${workingdir} && node ${selectedfile}");
                    return;
                }
                cmd = new String[]{"cmd", "/c",command};
            }else{
                command = command.replaceAll("\\$\\{selectedfile\\}", fo.getNameExt());
                command = command.replaceAll("\\$\\{workingdir\\}", fo.getParent().getURL().getPath());
                cmd = new String[]{"/bin/sh", "-c", command};
            }
            
            IOColorLines.println(io, command, Color.lightGray);
            final Process proc = Runtime.getRuntime().exec(cmd);

            final BufferedReader read = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            final BufferedReader readerr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
           // proc.waitFor();
            // thx to Andrew Skiba http://andskiba.blogspot.com/2011/09/nodejs-plugin-for-netbeans-and-daemons.html
            // but maybe in future, threading in NB RPC
            final AtomicBoolean done = new AtomicBoolean(false);  
            new Thread(new Runnable() {  
                public void run() {  
                    try {  
                        while (!done.get()) { 
                            Thread.sleep(50);  
                            while (read.ready()) {  
                                out.println(read.readLine());  
                            }  
                            while (readerr.ready()) {  
                                printErrLine(readerr.readLine(), io);  
                                //erout.println(readerr.readLine());  
                            }  
                            
                        }  

                        read.close();  
                        readerr.close();  

                    } catch (Exception ex) {  
                        Exceptions.printStackTrace(ex);  
                    }  
                }  
            }).start();  
            new Thread(new Runnable() {  
                public void run() {  
                    try {  
                        proc.waitFor();  
                    } catch (InterruptedException ex) {  
                        Exceptions.printStackTrace(ex);  
                    }  
                    finally {  
                        done.set(true);  
                    }  
                }  
            }).start();  
            
//            while (read.ready()) {
//                out.println(read.readLine());
//            }
//            while (readerr.ready()) {
//                String line = readerr.readLine();
//                printErrLine(line, io);
//                //erout.println(readerr.readLine());
//            }

            
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        
    }

    private void printErrLine(String line, InputOutput io) throws IOException {
        String file = null;
        int lineNum = 0;
        int colNum = 0;
        Pattern mask = Pattern.compile("\\((.*\\.js):(\\d*)\\:(\\d*)\\)");
        Matcher m = mask.matcher(line);
        while (m.find()) {
            file = m.group(1);
            lineNum = Integer.parseInt(m.group(2));
            colNum = Integer.parseInt(m.group(3));
        }
        if (file == null) {
            IOColorLines.println(io, line, Color.RED);
            return;
        } else {
            IOColorLines.println(io, line, new JSOutputListener(this.fo,lineNum), true, Color.BLUE);
        }
    }

    protected int mode() {
        return CookieAction.MODE_EXACTLY_ONE;
    }

    public String getName() {
        return NbBundle.getMessage(RunNode.class, "CTL_RunNode");
    }

    protected Class[] cookieClasses() {
        return new Class[]{DataObject.class};
    }

    @Override
    protected void initialize() {
        super.initialize();
        // see org.openide.util.actions.SystemAction.iconResource() Javadoc for more details
        putValue("noIconInMenu", Boolean.TRUE);
    }

    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }
}