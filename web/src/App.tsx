import { BrowserRouter } from "react-router-dom";
import { AppRoutes } from "./AppRoutes";

export function App(): JSX.Element {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  );
}