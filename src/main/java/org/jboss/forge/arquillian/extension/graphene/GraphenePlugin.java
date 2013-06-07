package org.jboss.forge.arquillian.extension.graphene;

import java.io.FileNotFoundException;

import javax.enterprise.event.Event;
import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.jboss.forge.arquillian.extension.drone.DroneFacet;
import org.jboss.forge.parser.JavaParser;
import org.jboss.forge.parser.java.Annotation;
import org.jboss.forge.parser.java.Field;
import org.jboss.forge.parser.java.JavaClass;
import org.jboss.forge.parser.java.JavaSource;
import org.jboss.forge.project.Project;
import org.jboss.forge.project.facets.JavaSourceFacet;
import org.jboss.forge.project.facets.events.InstallFacets;
import org.jboss.forge.resources.DirectoryResource;
import org.jboss.forge.resources.Resource;
import org.jboss.forge.resources.java.JavaResource;
import org.jboss.forge.shell.PromptType;
import org.jboss.forge.shell.Shell;
import org.jboss.forge.shell.ShellMessages;
import org.jboss.forge.shell.events.PickupResource;
import org.jboss.forge.shell.plugins.Alias;
import org.jboss.forge.shell.plugins.Command;
import org.jboss.forge.shell.plugins.Current;
import org.jboss.forge.shell.plugins.Help;
import org.jboss.forge.shell.plugins.Option;
import org.jboss.forge.shell.plugins.PipeOut;
import org.jboss.forge.shell.plugins.Plugin;
import org.jboss.forge.shell.plugins.RequiresFacet;
import org.jboss.forge.shell.plugins.RequiresProject;
import org.jboss.forge.shell.plugins.SetupCommand;
import org.jboss.forge.shell.util.ResourceUtil;

/**
 * @author Jérémie Lagarde
 * 
 */
@Alias("arq-graphene")
@RequiresFacet({ JavaSourceFacet.class, DroneFacet.class, GrapheneFacet.class })
@RequiresProject
@Help("A plugin that helps setting up Arquillian Graphene extension")
public class GraphenePlugin implements Plugin
{

   @Inject
   private Project project;

   @Inject
   private Event<InstallFacets> request;

   @Inject
   private Event<PickupResource> pickup;

   @Inject
   @Current
   private Resource<?> currentResource;

   @Inject
   private Shell shell;

   @SetupCommand
   public void setup(final PipeOut out)
   {

      if (!project.hasFacet(GrapheneFacet.class))
      {
         request.fire(new InstallFacets(GrapheneFacet.class));
      }
      if (project.hasFacet(GrapheneFacet.class))
      {
         ShellMessages.success(out, "Graphene arquillian extension is installed.");
      }
   }


   @Command(value = "new-page", help = "Create a new graphene page class")
   public void createTest(
            @Option(required = false,
                     help = "the package in which to build this page class",
                     description = "source package",
                     type = PromptType.JAVA_PACKAGE,
                     name = "package") final String packageName,
            @Option(required = true, name = "named", help = "the page class name") String name,
            final PipeOut out)
            throws Exception
   {
      if (!StringUtils.endsWith(name, "Page"))
      {
         name = name + "Page";
      }
      final JavaSourceFacet java = project.getFacet(JavaSourceFacet.class);

      String pagePackage;

      if ((packageName != null) && !"".equals(packageName))
      {
         pagePackage = packageName;
      }
      else if (getPackagePortionOfCurrentDirectory() != null)
      {
         pagePackage = getPackagePortionOfCurrentDirectory();
      }
      else
      {
         pagePackage = shell.promptCommon(
                  "In which package you'd like to create this Page, or enter for default",
                  PromptType.JAVA_PACKAGE, java.getBasePackage() +".pages");
      }

      JavaClass javaClass = JavaParser.create(JavaClass.class)
               .setPackage(pagePackage)
               .setName(name)
               .setPublic();
      
      Field<JavaClass> field = javaClass.addField();
      field.setName("root").setPrivate().setType("org.openqa.selenium.WebElement").addAnnotation("org.jboss.arquillian.graphene.spi.annotations.Root");
      
      JavaResource javaFileLocation = java.saveTestJavaSource(javaClass);

      shell.println("Created Page [" + javaClass.getQualifiedName() + "]");

      /**
       * Pick up the generated resource.
       */
      shell.execute("pick-up " + javaFileLocation.getFullyQualifiedName().replace(" ", "\\ "));
   }

   @Command(value = "new-element", help = "Create a new graphene page class")
   public void newElement(
            @Option(required = true, name = "named", help = "the element name") String name,
            @Option(required = true, name = "findby", help = "the element name") String findBy,
            final PipeOut out)
            throws Exception
   {
      final JavaSourceFacet java = project.getFacet(JavaSourceFacet.class);

      JavaClass javaClass = getJavaClass();

      Field<JavaClass> field = javaClass.addField();
      Annotation<JavaClass> annotation = field.setName(name).setPrivate().setType("org.openqa.selenium.WebElement").addAnnotation("org.openqa.selenium.support.FindBy");
      annotation.setStringValue(findBy.split("=")[0], findBy.split("=")[1]);
      java.saveTestJavaSource(javaClass);

      shell.println("Created element  [" + field.getName() + "]");

   }

   /**
    * Retrieves the package portion of the current directory if it is a package, null otherwise.
    *
    * @return String representation of the current package, or null
    */
   private String getPackagePortionOfCurrentDirectory()
   {
      for (DirectoryResource r : project.getFacet(JavaSourceFacet.class).getSourceFolders())
      {
         final DirectoryResource currentDirectory = shell.getCurrentDirectory();
         if (ResourceUtil.isChildOf(r, currentDirectory))
         {
            // Have to remember to include the last slash so it's not part of the package
            return currentDirectory.getFullyQualifiedName().replace(r.getFullyQualifiedName() + "/", "")
                     .replaceAll("/", ".");
         }
      }
      return null;
   }

   private JavaClass getJavaClass() throws FileNotFoundException
   {
      Resource<?> resource = shell.getCurrentResource();
      if (resource instanceof JavaResource)
      {
         return getJavaClassFrom(resource);
      }
      else
      {
         throw new RuntimeException("Current resource is not a JavaResource!");
      }

   }

   private JavaClass getJavaClassFrom(final Resource<?> resource) throws FileNotFoundException
   {
      JavaSource<?> source = ((JavaResource) resource).getJavaSource();
      if (!source.isClass())
      {
         throw new IllegalStateException("Current resource is not a JavaClass!");
      }
      return (JavaClass) source;
   }
}