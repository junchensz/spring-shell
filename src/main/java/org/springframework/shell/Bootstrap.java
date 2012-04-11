/*
 * Copyright (C) 2011 VMware, Inc.  All rights reserved. -- VMware Confidential
 */
package org.springframework.shell;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.roo.shell.CommandMarker;
import org.springframework.roo.shell.Converter;
import org.springframework.roo.shell.ExitShellRequest;
import org.springframework.roo.shell.event.ShellStatus;
import org.springframework.roo.support.logging.HandlerUtils;
import org.springframework.roo.support.util.Assert;
import org.springframework.util.StopWatch;



/**
 * Main class, needs some cleanup
 * 
 * @author vnagaraja
 * @author Jarred Li
 */
public class Bootstrap {

    private static Bootstrap bootstrap;
    //TODO using JLineShellComponenet to override and get access to "getSimpleParser" method on the shell - look into autowire...and move back to reference Shell interface.
    private JLineShellComponent shell;
    private ConfigurableApplicationContext ctx;
    private static StopWatch sw = new StopWatch("Spring Sehll");

    public static void main(String[] args) throws IOException {
        sw.start();        
        SimpleShellCommandLineOptions options = SimpleShellCommandLineOptions.parseCommandLine(args);

        for (Map.Entry<String, String> entry : options.extraSystemProperties.entrySet()) {
            System.setProperty(entry.getKey(), entry.getValue());
        }
        ExitShellRequest exitShellRequest;
        try {
            bootstrap = new Bootstrap(options.applicationContextLocation);
            exitShellRequest = bootstrap.run(options.executeThenQuit);
        } catch (RuntimeException t) {
            throw t;
        } finally {
            HandlerUtils.flushAllHandlers(Logger.getLogger(""));
        }

        System.exit(exitShellRequest.getExitCode());
    }

    public Bootstrap(String applicationContextLocation) throws IOException {

        //setupLogging();

        Assert.hasText(applicationContextLocation, "Application context location required");

        createApplicationContext(applicationContextLocation);

        
        //shell = new JLineShellComponent();
        shell = ctx.getBean("shell", JLineShellComponent.class);


        Map<String, CommandMarker> commands = BeanFactoryUtils.beansOfTypeIncludingAncestors(ctx,CommandMarker.class);
        

        for (CommandMarker command : commands.values()) {    
           //System.out.println("Registering command " + command);
           shell.getSimpleParser().add(command);
        }

        Map<String, Converter> converters = BeanFactoryUtils.beansOfTypeIncludingAncestors(ctx,Converter.class);

        for (Converter converter : converters.values()) {
          //System.out.println("Registering converter " + converter);
          shell.getSimpleParser().add(converter);
        }  
        
        
        shell.start();
        //TODO use listener and latch..
        while(true) {
        	//System.out.println("shell status = " + shell.getShellStatus().getStatus());
        	if (shell.getShellStatus().getStatus().equals(ShellStatus.Status.USER_INPUT)) {
        		break;
        	} else {        		
    			try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        	}
        }
    }

	private void createApplicationContext(String applicationContextLocation) {		
		AnnotationConfigApplicationContext annctx = new AnnotationConfigApplicationContext();		
		createAndRegisterBeanDefinition(annctx, org.springframework.roo.shell.converters.StringConverter.class);		
		createAndRegisterBeanDefinition(annctx, org.springframework.roo.shell.converters.AvailableCommandsConverter.class);
		createAndRegisterBeanDefinition(annctx, org.springframework.roo.shell.converters.BigDecimalConverter.class);
		createAndRegisterBeanDefinition(annctx, org.springframework.roo.shell.converters.BigIntegerConverter.class);
		createAndRegisterBeanDefinition(annctx, org.springframework.roo.shell.converters.BooleanConverter.class);
		createAndRegisterBeanDefinition(annctx, org.springframework.roo.shell.converters.CharacterConverter.class);
		createAndRegisterBeanDefinition(annctx, org.springframework.roo.shell.converters.DateConverter.class);
		createAndRegisterBeanDefinition(annctx, org.springframework.roo.shell.converters.DoubleConverter.class);
		createAndRegisterBeanDefinition(annctx, org.springframework.roo.shell.converters.EnumConverter.class);
		createAndRegisterBeanDefinition(annctx, org.springframework.roo.shell.converters.FloatConverter.class);
		createAndRegisterBeanDefinition(annctx, org.springframework.roo.shell.converters.IntegerConverter.class);
		createAndRegisterBeanDefinition(annctx, org.springframework.roo.shell.converters.LocaleConverter.class);
		createAndRegisterBeanDefinition(annctx, org.springframework.roo.shell.converters.LongConverter.class);
		createAndRegisterBeanDefinition(annctx, org.springframework.roo.shell.converters.ShortConverter.class);
		createAndRegisterBeanDefinition(annctx, org.springframework.roo.shell.converters.StaticFieldConverterImpl.class);
		createAndRegisterBeanDefinition(annctx, org.springframework.shell.JLineShellComponent.class, "shell");
		createAndRegisterBeanDefinition(annctx, org.springframework.shell.converters.SimpleFileConverter.class);
		
		annctx.scan("org.springframework.shell.commands");
		annctx.scan("org.springframework.shell.converters");
		annctx.refresh();
		
		ctx = initPluginApplicationContext(annctx);
		ctx.refresh();

	}

	/**
	 * init plugin ApplicactionContext
	 * 
	 * @param annctx parent ApplicationContext in core spring shell
	 * @return new ApplicationContext in the plugin with core spring shell's context as parent
	 */
	private ConfigurableApplicationContext initPluginApplicationContext(AnnotationConfigApplicationContext annctx) {
		ClassPathXmlApplicationContext subContext = new ClassPathXmlApplicationContext("spring-shell-beans.xml");
		subContext.setParent(annctx);
		return subContext;
	}
	
	
	
	protected void createAndRegisterBeanDefinition(AnnotationConfigApplicationContext annctx, Class clazz){
		createAndRegisterBeanDefinition(annctx,clazz,null);
	}
	
	protected void createAndRegisterBeanDefinition(AnnotationConfigApplicationContext annctx, Class clazz,String name) {
		RootBeanDefinition rbd = new RootBeanDefinition();
		rbd.setBeanClass(clazz);
		if(name != null){
			annctx.registerBeanDefinition(name, rbd);
		}
		else{
			annctx.registerBeanDefinition(clazz.getSimpleName(), rbd);
		}
	}

    protected ExitShellRequest run(String[] executeThenQuit) {
        
        ExitShellRequest exitShellRequest;
        
        if (null != executeThenQuit) {
            boolean successful = false;
            exitShellRequest = ExitShellRequest.FATAL_EXIT;

            for(String cmd : executeThenQuit) {
                successful = shell.executeCommand(cmd);
                if(!successful)
                    break;
            }

            //if all commands were successful, set the normal exit status
            if (successful) {
                exitShellRequest = ExitShellRequest.NORMAL_EXIT;
            }
        } else {
            shell.promptLoop();
            exitShellRequest = shell.getExitShellRequest();
            if (exitShellRequest == null) {
                // shouldn't really happen, but we'll fallback to this anyway
                exitShellRequest = ExitShellRequest.NORMAL_EXIT;
            }
        }

        ctx.close();
        sw.stop();
        if (shell.isDevelopmentMode()) {
            System.out.println("Total execution time: " + sw.getLastTaskTimeMillis() + " ms");
        }
        return exitShellRequest;
    }

}
