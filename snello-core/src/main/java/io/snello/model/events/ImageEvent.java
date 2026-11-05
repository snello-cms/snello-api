package io.snello.model.events;

public class ImageEvent {

    public String uuid;

    public String format;

    public ImageEvent(String uuid, String format) {
        this.uuid = uuid;
        this.format = format;
    }

    @Override
    public String toString() {
        return "ImageEvent{" +
                "uuid='" + uuid + '\'' +
                ", format='" + format + '\'' +
                '}';
    }
    public boolean isNotValid() {
        return this.uuid.isBlank() || this.format.isBlank();
    }
}
