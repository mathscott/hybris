
/*
 * [y] hybris Platform
 *
 * Copyright (c) 2017 SAP SE or an SAP affiliate company.  All rights reserved.
 *
 * This software is the confidential and proprietary information of SAP
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with SAP.
 */

import static de.hybris.platform.orchestration.Orchestration.inWorkDir;
import static de.hybris.platform.orchestration.status.StatusCheckers.http;
import static de.hybris.platform.orchestration.status.StatusCheckers.jdbc;
import static org.hamcrest.CoreMatchers.is;

import de.hybris.platform.orchestration.Orchestration;
import de.hybris.platform.orchestration.Orchestrator;
import de.hybris.platform.orchestration.OrchestratorException;
import de.hybris.platform.orchestration.configuration.InstanceConfiguration;
import de.hybris.platform.ui.HacActions;
import de.hybris.platform.ui.backoffice.BackofficeActions;
import de.hybris.platform.ui.backoffice.BackofficeWalker;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;


public class RollingUpdateTest
{

	static
	{
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
	}

	private Orchestrator platformNode1;
	private Orchestrator platformNode2;
	private Orchestrator Y2YplatformNode1;
	private Orchestrator Y2YplatformNode2;

	private ExecutorService executor;
	private final BackofficeWalker walker = new BackofficeWalker();
	Integer idOfNodeWhichStayedAlive = null;
	static Integer IDofPlatformY2YOrchestratorNode1 = 66;

	private static final Logger logger = LoggerFactory.getLogger(RollingUpdateTest.class);

	@Test
	public void shouldPerformRollingUpdate() throws InterruptedException, IOException
	{
		try (final Orchestration orchestration = Orchestration.build())
		{

			final Orchestrator modClusterDb = dbOrchestrator(orchestration);
			final Orchestrator modClusterLoadBalancer = modClusterLoadBalancerOrchestrator(orchestration);

			modClusterDb.start();
			modClusterDb.assureStarted();

			final Orchestrator platformInitializer = initPlatformBackofficeOrchestrator(orchestration);
			platformInitializer.start();
			platformInitializer.waitForFinish();

			modClusterLoadBalancer.start();
			platformNode1 = platformOrchestratorNode1(orchestration);
			platformNode1.start();
			platformNode2 = platformOrchestratorNode2(orchestration);
			platformNode2.start();

			modClusterLoadBalancer.assureStarted();

			final Orchestrator selenium = seleniumOrchestrator(orchestration);

			selenium.start();
			selenium.assureStarted();

			waitForClusterToHaveTwoNodesThroughHac();
			verifyBackofficeDoesNotHaveY2YFunctionality();

			executor = Executors.newSingleThreadExecutor();
			executor.submit(walker);

			int counter = 0;
			while (walker.getCurrentJvmSessionID() == null)
			{
				Thread.sleep(6000);
				logger.info("Waiting for backoffice to load: " + counter);
				counter++;
				if ((counter > 30) || walker.getException() != null)
				{
					throw new RuntimeException("Backoffice failed to load");
				}
			}

			logger.info("Finished warming up user interaction with backoffice");

			idOfNodeWhichStayedAlive = killNodeUsedByUser();
			walker.handleServerKill(idOfNodeWhichStayedAlive);

			final Orchestrator createTypeSystemOrchestrator = createTypeSystemOrchestrator(orchestration);
			createTypeSystemOrchestrator.start();
			createTypeSystemOrchestrator.waitForFinish();

			final Orchestrator updateSystemY2YSyncOrchestrator = updateSystemY2YSyncOrchestrator(orchestration);
			updateSystemY2YSyncOrchestrator.start();
			updateSystemY2YSyncOrchestrator.waitForFinish();

			Y2YplatformNode1 = platformY2YOrchestratorNode1(orchestration);
			Y2YplatformNode1.start();
			Y2YplatformNode1.assureStarted();

			waitForClusterToHaveTwoNodesThroughHac();

			killSecondNodeOfInitialConfiguration(idOfNodeWhichStayedAlive);

			walker.handleServerKill(IDofPlatformY2YOrchestratorNode1);

			Y2YplatformNode2 = platformY2YOrchestratorNode2(orchestration);
			Y2YplatformNode2.start();
			Y2YplatformNode2.assureStarted();

			waitForClusterToHaveTwoNodesThroughHac();
			verifyBackofficeHaveY2YFunctionality();

			Assert.assertNull(walker.getException());

		}
		catch (final RuntimeException e)
		{
			logger.info("Unexpected exception ");
			logger.info(e.getClass().getName());
			logger.info(e.getMessage());
		}
		finally
		{
			if (walker != null)
			{
				walker.stop();
			}
			executor.shutdown();
			executor.awaitTermination(10, TimeUnit.SECONDS);
			logger.info("RollingUpdateTest.shouldPerformRollingUpdate test case finished successfully");
		}

	}


	private int killNodeUsedByUser()
	{
		if (walker.getCurrentJvmSessionID().get() == 1)
		{
			platformNode1.kill();
			logger.info("Killing node the user is using. Killed node id is 1");
			return 2;
		}
		else if (walker.getCurrentJvmSessionID().get() == 2)
		{
			platformNode2.kill();
			logger.info("Killing node the user is using. Killed node id is 2");
			return 1;
		}
		throw new RuntimeException("Unexpected node ID: " + walker.getCurrentJvmSessionID().get());
	}

	private void killSecondNodeOfInitialConfiguration(final int nodeToKill)
	{
		if (nodeToKill == 2)
		{
			platformNode2.kill();
		}
		else
		{
			platformNode1.kill();
		}

	}

	private void waitForClusterToHaveTwoNodesThroughHac() throws InterruptedException
	{
		logger.info("Logging to Hac");
		final WebDriver driver = createDriver();
		logger.info("Navigating to https://load_balancer/ where Hac should be");
		driver.navigate().to("https://load_balancer/");
		final WebDriverWait webDriverWait = new WebDriverWait(driver, 10L);
		final WebElement inputUsername = webDriverWait.until(ExpectedConditions.visibilityOfElementLocated(By.name("j_username")));
		Assert.assertEquals(inputUsername.getAttribute("value").toString(), "admin");

		final WebElement inputPass = webDriverWait.until(ExpectedConditions.visibilityOfElementLocated(By.name("j_password")));
		inputPass.clear();
		inputPass.sendKeys(new CharSequence[]
				{ "nimda" });
		final WebElement buttonLogIn = webDriverWait
				.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("button[type=\"submit\"]")));
		buttonLogIn.click();
		webDriverWait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#monitoring")));

		logger.info("Logged to Hac in successful");

		final HacActions hac = new HacActions(driver);
		logger.info("Navigating to cluster details to verify two nodes are up");

		final int howLongWaitForCluster = 100;
		for (int i = 0; i < howLongWaitForCluster; i++)
		{
			logger.info("Expecting cluster to have two nodes");
			Thread.sleep(1000);
			hac.navigateToDatabaseProperties();
			hac.navigateToClusterProperties();
			final List<WebElement> clusterNodes = driver.findElements(By.cssSelector("#clusternodes > tbody > tr"));
			if (clusterNodes.size() == 2)
			{
				logger.info("Cluster does have two nodes");
				driver.quit();
				return;
			}
		}
		final List<WebElement> clusterNodes = driver.findElements(By.cssSelector("#clusternodes > tbody > tr"));
		logger.info("Assert cluster size " + clusterNodes.size());
		driver.quit();
		Assert.assertThat(clusterNodes.size(), is(2));
	}

	private int verifyBackofficeDoesNotHaveY2YFunctionality()
	{

		final WebDriver driver = createDriver();
		goToBackoffice(driver);
		logger.info("Verify backoffice DOES NOT have y2ysync functionality");
		final By y2ySyncMenu = By.xpath(".//span[@ytestid='hmc_treenode_y2ysync']");
		new WebDriverWait(driver, 1)
				.until(ExpectedConditions.not(ExpectedConditions.visibilityOfAllElementsLocatedBy(y2ySyncMenu)));
		final int sessionID =  BackofficeActions.getJvmSessionID(driver);
		driver.quit();
		return sessionID;
	}

	private void verifyBackofficeHaveY2YFunctionality()
	{
		final WebDriver driver = createDriver();
		goToBackoffice(driver);
		logger.info("Verify backoffice does have y2ysync functionality");
		final By y2ySyncMenu = By.xpath(".//span[@ytestid='hmc_treenode_y2ysync']");
		new WebDriverWait(driver, 10).until(ExpectedConditions.visibilityOfAllElementsLocatedBy(y2ySyncMenu));
		driver.quit();

	}

	private void goToBackoffice(final WebDriver driver)
	{
		logger.info("Navigating to https://load_balancer/backoffice");
		driver.navigate().to("https://load_balancer/backoffice");


		BackofficeActions.logIntoBackofficeAsAdmin(driver);

	}


	private WebDriver createDriver()
	{
		try
		{
			return new RemoteWebDriver(new URL("http://localhost:4444/wd/hub"), DesiredCapabilities.chrome());
		}
		catch (final MalformedURLException e)
		{
			throw new OrchestratorException(e);
		}
	}

	public static Orchestrator dbOrchestrator(final Orchestration orchestration)
	{
		final InstanceConfiguration configuration = orchestration.container() //
				.fromImage("rolling_update_test_hsql") //
				.withVolume("/opt/hsqldb/data") //
				.instance().withName("hsql") //
				.mapPort(9090, 9090) //
				.checkStatusVia(jdbc("jdbc:hsqldb:hsql://localhost:9090/hybris", "hybris", "hybris")) //
				.configure();

		return orchestration.onLocalDocker(configuration);
	}

	public static Orchestrator modClusterLoadBalancerOrchestrator(final Orchestration orchestration)
	{
		final InstanceConfiguration configuration = orchestration.container() //
				.fromImage("rolling_update_test_mod_cluster_apache") //
				.withEnv("HTTPD_LOG_LEVEL=debug")//
				.instance() //
				.copyDirectory(inWorkDir("/resources/secrets"), "/opt/httpd-build/conf/ssl") //
				.withName("load_balancer") //
				.mapPort(80, 80) //
				.mapPort(443, 443) //
				.mapPort(6666, 7777) //
				.checkStatusVia(http("https://localhost/", "Remember Login")) //
				.configure();

		return orchestration.onLocalDocker(configuration);
	}

	public static Orchestrator initPlatformBackofficeOrchestrator(final Orchestration orchestration)
	{
		final InstanceConfiguration configuration = orchestration.container() //
				.fromImage("rolling_update_test_platformbackoffice") //
				.withNamedVolume("mediavolume", "/opt/hybris/data/media") //
				.linkContainers("hsql") //
				.executeCommands("admin", "initialize") //
				.instance() //
				.withName("platform_admin_init") //
				.mapPort(8088, 8088) //
				.mapPort(8081, 8081) //
				.checkStatusVia(http("https://localhost:8088/")) //
				.configure();

		return orchestration.onLocalDocker(configuration);
	}

	public static Orchestrator createTypeSystemOrchestrator(final Orchestration orchestration)
	{
		final InstanceConfiguration configuration = orchestration.container() //
				.fromImage("rolling_update_test_platformbackoffice") //
				.withNamedVolume("mediavolume", "/opt/hybris/data/media") //
				.linkContainers("hsql") //
				.executeCommands("admin", "createtypesystem", "-DtypeSystemName=new_type_system") //
				.instance() //
				.withName("platform_admin_createtypesystem") //
				.mapPort(8088, 8088) //
				.mapPort(8081, 8081) //
				.checkStatusVia(http("https://localhost:8088/")) //
				.configure();

		return orchestration.onLocalDocker(configuration);
	}

	public static Orchestrator platformOrchestratorNode1(final Orchestration orchestration)
	{
		final InstanceConfiguration configuration = orchestration.container() //
				.fromImage("rolling_update_test_platformbackoffice") //
				.withNamedVolume("mediavolume", "/opt/hybris/data/media") //
				.withEnv("Y_JVM_ROUTE=1") //
				.withEnv("JVM_ROUTE=1") //
				.withEnv("MOD_CLUSTER_PROXY_LIST=load_balancer:6666") //
				.linkContainers("hsql", "load_balancer").executeCommands("platform") //
				.instance() //
				.withName("rolling_update_platformbackoffice_node_1") //
				.configure();

		return orchestration.onLocalDocker(configuration);
	}

	public static Orchestrator platformOrchestratorNode2(final Orchestration orchestration)
	{
		final InstanceConfiguration configuration = orchestration.container() //
				.fromImage("rolling_update_test_platformbackoffice") //
				.withNamedVolume("mediavolume", "/opt/hybris/data/media") //
				.withEnv("Y_JVM_ROUTE=2") //
				.withEnv("JVM_ROUTE=2") //
				.withEnv("MOD_CLUSTER_PROXY_LIST=load_balancer:6666") //
				.linkContainers("hsql", "load_balancer").executeCommands("platform") //
				.instance() //
				.withName("rolling_update_platformbackoffice_node_2") //
				.configure();

		return orchestration.onLocalDocker(configuration);
	}

	public static Orchestrator updateSystemY2YSyncOrchestrator(final Orchestration orchestration)
	{
		final InstanceConfiguration configuration = orchestration.container() //
				.fromImage("rolling_update_test_y2ysyncbackoffice") //
				.withNamedVolume("mediavolume", "/opt/hybris/data/media") //
				.linkContainers("hsql") //
				.executeCommands("admin", "updatesystem") //
				.instance() //
				.withName("platform_y2y_admin_init") //
				.mapPort(8088, 8088) //
				.mapPort(8081, 8081) //
				.checkStatusVia(http("https://localhost:8088/")) //
				.configure();

		return orchestration.onLocalDocker(configuration);
	}

	public static Orchestrator platformY2YOrchestratorNode1(final Orchestration orchestration)
	{
		final InstanceConfiguration configuration = orchestration.container() //
				.fromImage("rolling_update_test_y2ysyncbackoffice") //
				.withNamedVolume("mediavolume", "/opt/hybris/data/media") //
				.withEnv("Y_JVM_ROUTE=" + String.valueOf(IDofPlatformY2YOrchestratorNode1)) //
				.withEnv("JVM_ROUTE=66") //
				.withEnv("MOD_CLUSTER_PROXY_LIST=load_balancer:6666") //
				.linkContainers("hsql", "load_balancer").executeCommands("platformUpdate") //
				.instance() //
				.withName("rolling_update_y2y_node_1") //
				.checkStatusVia(http("http://localhost:7777/mcm", "Node 66")) //
				.configure();

		return orchestration.onLocalDocker(configuration);
	}

	public static Orchestrator platformY2YOrchestratorNode2(final Orchestration orchestration)
	{
		final InstanceConfiguration configuration = orchestration.container() //
				.fromImage("rolling_update_test_y2ysyncbackoffice") //
				.withNamedVolume("mediavolume", "/opt/hybris/data/media") //
				.withEnv("Y_JVM_ROUTE=44") //
				.withEnv("JVM_ROUTE=44") //
				.withEnv("MOD_CLUSTER_PROXY_LIST=load_balancer:6666") //
				.linkContainers("hsql", "load_balancer").executeCommands("platformUpdate") //
				.instance() //
				.withName("rolling_update_y2y_node_2") //
				.checkStatusVia(http("http://localhost:7777/mcm", "Node 44")) //
				.configure();

		return orchestration.onLocalDocker(configuration);
	}

	public static Orchestrator seleniumOrchestrator(final Orchestration orchestration)
	{
		final InstanceConfiguration configuration = orchestration.container() //
				.fromImage("selenium/standalone-chrome-debug:3.1.0-astatine") //
				.linkContainers("load_balancer").withEnv("NODE_MAX_SESSION=2").instance().withName("selenium") //
				.mapPort(5900, 5900) //
				.mapPort(4444, 4444) //
				.checkStatusVia(http("http://localhost:4444/wd/hub/static/resource/hub.html")).configure();

		return orchestration.onLocalDocker(configuration);
	}
}
