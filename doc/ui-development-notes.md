## UI Architecure pattern:
We use a variant of the **Presentation Model** pattern which has some similarities with the **Model View ViewModel** 
(MVVM used in Silverlight and WPF) as we use data bindings, though there are differences in the way the view and 
the "code behind" is organized (due to different framework features/support).  

We don't use the term "controller" for the JavaFX controller it has too much association with the classical MVC 
controller but instead we use the term "code behind" as it describes better that this class is just a code extension of 
the declarative view format.  
That interpretation is also confirmed by the Java FX reference: "A controller is a compiled class that implements the 
"code behind" the object hierarchy defined by the document."
 

The described pattern is only applied yet to that package:  
io.bisq.gui.main.offer.createoffer;  
For prototyping the UI we stick first with a more rapid development style approach.  


### Elements
* View
* CodeBehind (CB)
* Presentation Model (PM)
* Model

##### Overview: 
* View/CB is responsible for the visual representation. No logic. No state.
* Presentation model holds the view/CB state.
* Presentation model handles view specific logic.
* Presentation model does validation of user in put and formatting of domain data.
* Model is the domain specific representation of the view. It holds domain data and handles domain logic.


### View
Typically FXML is used.    
 
It knows the CodeBehind (fx:controller)  

##### Responsibility:  
* Defines visible parts of the UI  
* Define UI element properties  
* Layout  
* Style  
* Event handler definition  


### CodeBehind (CB)
It is conceptually part of the view layer. It adds functionality which cannot be expressed in the declarative FXML 
format.    
It is the JavaFX controller associated with the FXML view, but we don't use the term controller as it has too much 
association with the classical MVC controller. It gets created by the JavaFX framework (FXMLLoader) and also the 
setup with the FXML view is done by the framework.   

It knows the presentation model (via Guice injection) but not the model. 

##### Responsibility:  
* Setup binding for updates from PM to view elements (also bidirectional for used for input data). 
* Those binding are only simple bindings to plain presenter properties, no logical bindings.
* Listens to UI events (Actions) from UI controls and calls method in presentation model.
* Is entry node for view graph and responsible for navigation and for creation of new views. 
* Passes application method calls to PM. Calls application methods on sub views.
* Handle lifecycle and self removal from scene graph.
* Can contain non-declarative (dynamic) view definitions (if that gets larger, then use a dedicated ViewBuilder)
* Has **no logic** and **no state**!


### Presentation model (PM)
It is the abstraction/presentation of the view.      
Can be used for unit testing.  

It knows the model (via Guice injection) but it does not know the CodeBehind (View)

##### Responsibility:
* Holds the state of the view/CB
* Formats domain data to the needed representation for the view/CB.
* Receive user input via method calls from CodeBehind.
* Validates UI input, applies business logic and converts (parse) input to model.
* Listen to updates from model via bindings.

 
### Data model
Represents the domain scope which is handled by the view.  
Is interface to application domain objects.  
We use Guice for dependency injection of the application domain objects.  
Can be used for unit testing.  

Does not know the PM and View/CB

##### Responsibility:
* Holds domain data for that view
* Apply domain business logic
* Interacts with application domain objects
* It only covers the domain represented by that view and not a lower level domain. 


## References:
[Presentation Model](http://martinfowler.com/eaaDev/PresentationModel.html)  
[Model View ViewModel - MVVM](http://msdn.microsoft.com/en-us/magazine/dd419663.aspx)  
[Java FX FXML controllers]
(http://docs.oracle.com/javafx/2/api/javafx/fxml/doc-files/introduction_to_fxml.html#controllers)
