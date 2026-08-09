import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { LoginComponent } from './core/auth/login.component';

export const routes: Routes = [
    {
        path: "", // Default route
        redirectTo: "jobs/job-offer",
        pathMatch: "full",
    },
    {
        path: "login",
        component: LoginComponent,
    },
    {
        path: "jobs",
        canActivate: [authGuard],
        loadChildren: () =>
            import("./features/job-offer/job-offer.routes").then((m) => m.JOB_ROUTES),
    },
    {
        path: "candidates",
        canActivate: [authGuard],
        loadChildren: () =>
            import("./features/cv-ingestion/cv-ingestion.routes").then((m) => m.CV_INGESTION_ROUTES),
    },
    { path: "**", redirectTo: "jobs/job-offer" },
];
