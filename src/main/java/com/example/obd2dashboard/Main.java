package com.example.obd2dashboard;

import com.example.obd2dashboard.controller.DashboardController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public final class Main extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/view/dashboard.fxml"));
        Scene scene = new Scene(loader.load(), 1280, 820);
        DashboardController controller = loader.getController();

        stage.setMinWidth(1050);
        stage.setMinHeight(720);
        stage.setScene(scene);
        controller.setStage(stage);
        stage.setOnCloseRequest(event -> controller.shutdown());
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
