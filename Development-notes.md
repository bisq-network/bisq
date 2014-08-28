## UI Architecure pattern:
We use the Presentation Model pattern which has some similarities with MVVM (Silverlight, WPF) as we use data 
bindings, though there are differences in the way the view and the "code behind" is organized (different framework 
support).
We don't use the term controller for the JavaFX controller it has too much association with the classical MVC 
controller. 

View: FXML or code based View
CodeBehind (CB): JavaFX controller associated with FXML View
Presentation Model (PM)
Model: Domain data

* State is stored in the presenter.
* Logic is stored in presenter.
* Presenter represents a abstract view of the UI.
* Presenter is not aware of the view.
* View is aware of the presenter.
* View is completely isolated from the model.


## References:
[Presentation Model](http://martinfowler.com/eaaDev/PresentationModel.html)
[Model View ViewModel - MVVM](http://msdn.microsoft.com/en-us/magazine/dd419663.aspx)
